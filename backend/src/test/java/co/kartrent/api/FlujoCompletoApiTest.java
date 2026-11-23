package co.kartrent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.kartrent.aplicacion.puertos.RepositorioSesiones;
import co.kartrent.dominio.comun.ConflictoConcurrenciaException;
import co.kartrent.infraestructura.pagos.VerificadorFirmaWebhook;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Prueba de integración de punta a punta: API REST + casos de uso + JDBC + Flyway sobre H2 (modo PostgreSQL).
 * Recorre HU-01 → HU-02 → HU-03 → HU-04 → HU-05.
 */
@SpringBootTest(properties = "kartrent.demo=false")
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlujoCompletoApiTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    VerificadorFirmaWebhook firma;
    @Autowired
    RepositorioSesiones sesiones;

    private ResultActions post(String url, String cuerpo) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private JsonNode leer(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    @Order(1)
    void recorridoCompletoDeUnaTanda() throws Exception {
        LocalDateTime inicio = LocalDateTime.now(ZoneId.of("America/Bogota")).plusHours(2).truncatedTo(ChronoUnit.MINUTES);

        // HU-05: programar tanda (la tarifa ADULTO viene de la migración V2)
        post("/api/v1/sesiones", """
                {"codigo":"T-TEST","inicio":"%s","duracionMinutos":10,"cupos":4,"tipoKart":"ADULTO"}""".formatted(inicio))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cuposDisponibles").value(4));

        // HU-01: registrar cliente, reservar y pagar
        post("/api/v1/clientes", """
                {"documento":"1085999001","nombre":"Laura Pantoja","correo":"laura@example.com","telefono":"3001112233",
                 "fechaNacimiento":"2000-03-10","aceptaExoneracion":true}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exoneracionFirmada").value(true));

        JsonNode reserva = leer(post("/api/v1/reservas", """
                {"documentoCliente":"1085999001","codigoSesion":"T-TEST","cupos":1}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_PAGO")));
        String codigo = reserva.get("codigo").asText();

        JsonNode pago = leer(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/reservas/" + codigo + "/pagos").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"metodo\":\"TARJETA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reserva.estado").value("CONFIRMADA")));
        assertThat(pago.get("factura").get("cufe").asText()).hasSize(96);

        // Mismo Idempotency-Key: misma factura, sin doble cobro y con 200 (no se creó un pago nuevo)
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/reservas/" + codigo + "/pagos").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"metodo\":\"TARJETA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factura.numero").value(pago.get("factura").get("numero").asText()));

        // HU-02: parrilla, RN-04 y asignación
        mvc.perform(get("/api/v1/sesiones/T-TEST/parrilla"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservas[0].codigoReserva").value(codigo));

        post("/api/v1/sesiones/T-TEST/asignaciones", """
                {"codigoReserva":"%s","documentoPiloto":"1085999001","numeroKart":7}""".formatted(codigo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Kart 7 no disponible"));

        post("/api/v1/sesiones/T-TEST/asignaciones", """
                {"codigoReserva":"%s","documentoPiloto":"1085999001","numeroKart":3}""".formatted(codigo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoTransponder").value("TR-103"));

        post("/api/v1/sesiones/T-TEST/iniciar", "").andExpect(jsonPath("$.estado").value("EN_CURSO"));

        // HU-03: telemetría
        Instant t0 = Instant.now();
        post("/api/v1/telemetria/pasos", "{\"codigoTransponder\":\"TR-103\",\"marcaTiempo\":\"%s\"}".formatted(t0))
                .andExpect(jsonPath("$.vueltaRegistrada").value(false));
        post("/api/v1/telemetria/pasos", "{\"codigoTransponder\":\"TR-103\",\"marcaTiempo\":\"%s\"}"
                .formatted(t0.plusMillis(33_210)))
                .andExpect(jsonPath("$.vueltaRegistrada").value(true))
                .andExpect(jsonPath("$.vuelta.tiempoMs").value(33210));
        post("/api/v1/telemetria/pasos", "{\"codigoTransponder\":\"TR-103\",\"marcaTiempo\":\"%s\"}"
                .formatted(t0.plusMillis(40_000)))
                .andExpect(jsonPath("$.vueltaRegistrada").value(false));

        mvc.perform(get("/api/v1/sesiones/T-TEST/clasificacion"))
                .andExpect(jsonPath("$.clasificacion[0].numeroKart").value(3))
                .andExpect(jsonPath("$.clasificacion[0].mejorVueltaMs").value(33210));

        // HU-04: bajar el umbral para forzar el preventivo y finalizar
        mvc.perform(put("/api/v1/flota/umbral").contentType(MediaType.APPLICATION_JSON).content("{\"horas\":19.9}"))
                .andExpect(jsonPath("$.horas").value(19.9));
        JsonNode cierre = leer(post("/api/v1/sesiones/T-TEST/finalizar", "")
                .andExpect(jsonPath("$.sesion.estado").value("FINALIZADA")));
        assertThat(cierre.get("ordenesGeneradas")).hasSize(1);
        String orden = cierre.get("ordenesGeneradas").get(0).get("numero").asText();

        post("/api/v1/ordenes/" + orden + "/asignacion", "{\"codigoMecanico\":\"M-02\"}")
                .andExpect(jsonPath("$.codigoMecanico").value("M-02"));
        post("/api/v1/ordenes/" + orden + "/cierre", "{\"codigoMecanico\":\"M-01\",\"observaciones\":\"x\"}")
                .andExpect(status().isConflict());
        post("/api/v1/ordenes/" + orden + "/cierre", "{\"codigoMecanico\":\"M-02\",\"observaciones\":\"Cambio de bujía\"}")
                .andExpect(jsonPath("$.estado").value("CERRADA"));

        mvc.perform(get("/api/v1/flota/karts"))
                .andExpect(jsonPath("$[2].numero").value(3))
                .andExpect(jsonPath("$[2].estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$[2].horasDesdeServicio").value(0.0));

        // HU-05: reporte
        mvc.perform(get("/api/v1/reportes/ocupacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuposVendidos").value(1));
    }

    @Test
    @Order(3)
    void validacionYSeguridadDelWebhook() throws Exception {
        post("/api/v1/clientes", "{\"documento\":\"\",\"nombre\":\"\"}").andExpect(status().isBadRequest());
        post("/api/v1/pagos/webhook", "{\"referencia\":\"KR-x\",\"aprobado\":true}")
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/sesiones/NO-EXISTE")).andExpect(status().isNotFound());
    }

    /** Contrato de errores del material: 400 datos inválidos, 404 no existe, 409 conflicto con el estado, 401 firma. */
    @Test
    @Order(2)
    void contratoDeErroresHttp() throws Exception {
        LocalDateTime inicio = LocalDateTime.now(ZoneId.of("America/Bogota")).plusHours(3).truncatedTo(ChronoUnit.MINUTES);

        // 400: JSON mal formado, enum desconocido, UUID mal formado y validaciones de campos
        post("/api/v1/tarifas", "{no es json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Cuerpo de la petición ilegible"));
        post("/api/v1/tarifas", "{\"tipoKart\":\"BANANA\",\"valorPorCupo\":1000}").andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reservas/no-es-uuid")).andExpect(status().isBadRequest());
        post("/api/v1/sesiones", "{\"codigo\":\"T-PASADA\",\"inicio\":\"2020-01-01T10:00:00\",\"duracionMinutos\":10,"
                + "\"cupos\":4,\"tipoKart\":\"ADULTO\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La tanda debe programarse a futuro"));

        // 404: recurso inexistente y ruta inexistente conservan su código
        post("/api/v1/reservas", "{\"documentoCliente\":\"0000\",\"codigoSesion\":\"T-ERR\",\"cupos\":1}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.title").value("Recurso no encontrado"));
        mvc.perform(get("/api/v1/no-existe")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/reservas")).andExpect(status().isMethodNotAllowed());

        // 409: duplicados y cupos insuficientes
        post("/api/v1/sesiones", """
                {"codigo":"T-ERR","inicio":"%s","duracionMinutos":10,"cupos":2,"tipoKart":"ADULTO"}""".formatted(inicio))
                .andExpect(status().isCreated());
        post("/api/v1/sesiones", """
                {"codigo":"T-ERR","inicio":"%s","duracionMinutos":10,"cupos":2,"tipoKart":"ADULTO"}""".formatted(inicio))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("Conflicto con el estado actual"));
        String cliente = """
                {"documento":"1085999002","nombre":"Mateo Ruiz","correo":"mateo@example.com","telefono":"3000000000",
                 "fechaNacimiento":"1999-05-20","aceptaExoneracion":true}""";
        post("/api/v1/clientes", cliente).andExpect(status().isCreated());
        post("/api/v1/clientes", cliente).andExpect(status().isConflict());
        post("/api/v1/reservas", "{\"documentoCliente\":\"1085999002\",\"codigoSesion\":\"T-ERR\",\"cupos\":3}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Cupos insuficientes"));

        // Reserva, factura aún inexistente (404 Problem Details) y pago rechazado que se puede reintentar
        String codigo = leer(post("/api/v1/reservas",
                "{\"documentoCliente\":\"1085999002\",\"codigoSesion\":\"T-ERR\",\"cupos\":1}")
                .andExpect(status().isCreated())).get("codigo").asText();
        mvc.perform(get("/api/v1/reservas/" + codigo + "/factura")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"));
        pagar(codigo, "SIMULAR_RECHAZO", "k-rechazo").andExpect(status().isCreated())
                .andExpect(jsonPath("$.pago.aprobado").value(false))
                .andExpect(jsonPath("$.reserva.estado").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.factura").doesNotExist());
        pagar(codigo, "TARJETA", "k-reintento").andExpect(status().isCreated())
                .andExpect(jsonPath("$.pago.aprobado").value(true))
                .andExpect(jsonPath("$.reserva.estado").value("CONFIRMADA"));
        mvc.perform(get("/api/v1/reservas/" + codigo + "/factura")).andExpect(status().isOk())
                .andExpect(jsonPath("$.cufe").isNotEmpty());
        pagar(codigo, "TARJETA", "k-otra-clave").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La reserva no está pendiente de pago"));

        // Webhook: firma válida con cuerpo ilegible = 400; referencia desconocida = 404; sin firma = 401
        String ilegible = "esto no es json";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/pagos/webhook")
                .header("X-Firma", firma.firmar(ilegible)).contentType(MediaType.APPLICATION_JSON).content(ilegible))
                .andExpect(status().isBadRequest());
        String desconocida = "{\"referencia\":\"KR-no-existe\",\"aprobado\":true}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/pagos/webhook")
                .header("X-Firma", firma.firmar(desconocida)).contentType(MediaType.APPLICATION_JSON).content(desconocida))
                .andExpect(status().isNotFound());
        post("/api/v1/pagos/webhook", desconocida).andExpect(status().isUnauthorized());
    }

    private ResultActions pagar(String codigo, String metodo, String clave) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/reservas/" + codigo + "/pagos").header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON).content("{\"metodo\":\"" + metodo + "\"}"));
    }

    /**
     * Descuento de cupos atómico: 8 clientes reservan 1 cupo a la vez en una tanda de 5. Sin control de concurrencia
     * dos lecturas simultáneas pisarían el contador (lost update) y se venderían más de 5; con el bloqueo optimista
     * por versión los perdedores se reintentan y la venta termina exactamente en 5, el resto recibe 409.
     */
    @Test
    @Order(4)
    void reservasSimultaneasNuncaVendenMasCuposQueLosDisponibles() throws Exception {
        LocalDateTime inicio = LocalDateTime.now(ZoneId.of("America/Bogota")).plusHours(4).truncatedTo(ChronoUnit.MINUTES);
        post("/api/v1/sesiones", """
                {"codigo":"T-CONC","inicio":"%s","duracionMinutos":10,"cupos":5,"tipoKart":"ADULTO"}""".formatted(inicio))
                .andExpect(status().isCreated());
        int clientes = 8;
        for (int i = 1; i <= clientes; i++) {
            post("/api/v1/clientes", """
                    {"documento":"10860000%02d","nombre":"Piloto %d","correo":"p%d@example.com","telefono":"300",
                     "fechaNacimiento":"1995-01-01","aceptaExoneracion":true}""".formatted(i, i, i))
                    .andExpect(status().isCreated());
        }

        ExecutorService hilos = Executors.newFixedThreadPool(clientes);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Integer>> respuestas = new ArrayList<>();
        for (int i = 1; i <= clientes; i++) {
            String documento = "10860000%02d".formatted(i);
            respuestas.add(hilos.submit(() -> {
                salida.await();
                return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentoCliente\":\"" + documento + "\",\"codigoSesion\":\"T-CONC\",\"cupos\":1}"))
                        .andReturn().getResponse().getStatus();
            }));
        }
        salida.countDown();
        int creadas = 0;
        int rechazadas = 0;
        for (Future<Integer> r : respuestas) {
            int estado = r.get();
            if (estado == 201) creadas++;
            else if (estado == 409) rechazadas++;
            else throw new AssertionError("Estado inesperado en una reserva concurrente: " + estado);
        }
        hilos.shutdown();

        assertThat(creadas).as("reservas creadas").isEqualTo(5);
        assertThat(rechazadas).as("reservas rechazadas por cupos").isEqualTo(3);
        mvc.perform(get("/api/v1/sesiones/T-CONC"))
                .andExpect(jsonPath("$.cuposVendidos").value(5))
                .andExpect(jsonPath("$.cuposDisponibles").value(0));
    }

    /** El repositorio rechaza guardar una tanda leída con una versión que otra transacción ya superó. */
    @Test
    @Order(5)
    void guardarConUnaVersionVencidaLanzaConflictoDeConcurrencia() throws Exception {
        LocalDateTime inicio = LocalDateTime.now(ZoneId.of("America/Bogota")).plusHours(5).truncatedTo(ChronoUnit.MINUTES);
        post("/api/v1/sesiones", """
                {"codigo":"T-VER","inicio":"%s","duracionMinutos":10,"cupos":4,"tipoKart":"ADULTO"}""".formatted(inicio))
                .andExpect(status().isCreated());

        var lecturaA = sesiones.buscar("T-VER").orElseThrow();
        var lecturaB = sesiones.buscar("T-VER").orElseThrow();
        assertThat(lecturaA.getVersion()).isEqualTo(lecturaB.getVersion());

        lecturaA.reservarCupos(1);
        sesiones.guardar(lecturaA);
        assertThat(lecturaA.getVersion()).isEqualTo(1);

        lecturaB.reservarCupos(1);
        org.junit.jupiter.api.Assertions.assertThrows(ConflictoConcurrenciaException.class, () -> sesiones.guardar(lecturaB));
        assertThat(sesiones.buscar("T-VER").orElseThrow().getCuposVendidos()).isEqualTo(1);
    }
}
