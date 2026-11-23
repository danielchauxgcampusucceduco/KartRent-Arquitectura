package co.kartrent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
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
class FlujoCompletoApiTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private ResultActions post(String url, String cuerpo) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private JsonNode leer(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserva.estado").value("CONFIRMADA")));
        assertThat(pago.get("factura").get("cufe").asText()).hasSize(96);

        // Mismo Idempotency-Key: misma factura, sin doble cobro
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/reservas/" + codigo + "/pagos").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"metodo\":\"TARJETA\"}"))
                .andExpect(jsonPath("$.factura.numero").value(pago.get("factura").get("numero").asText()));

        // HU-02: parrilla, RN-04 y asignación
        mvc.perform(get("/api/v1/sesiones/T-TEST/parrilla"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservas[0].codigoReserva").value(codigo));

        post("/api/v1/sesiones/T-TEST/asignaciones", """
                {"codigoReserva":"%s","documentoPiloto":"1085999001","numeroKart":7}""".formatted(codigo))
                .andExpect(status().isUnprocessableEntity())
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
                .andExpect(status().isUnprocessableEntity());
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
    void validacionYSeguridadDelWebhook() throws Exception {
        post("/api/v1/clientes", "{\"documento\":\"\",\"nombre\":\"\"}").andExpect(status().isBadRequest());
        post("/api/v1/pagos/webhook", "{\"referencia\":\"KR-x\",\"aprobado\":true}")
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/sesiones/NO-EXISTE")).andExpect(status().isNotFound());
    }
}
