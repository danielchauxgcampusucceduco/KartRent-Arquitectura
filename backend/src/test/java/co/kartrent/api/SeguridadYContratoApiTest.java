package co.kartrent.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Roles por historia de usuario, errores 401/403 en Problem Details, CORS y el contrato publicado en OpenAPI. */
@SpringBootTest(properties = "kartrent.demo=false")
@AutoConfigureMockMvc
class SeguridadYContratoApiTest {
    private static final String ADMIN_CLAVE = "clave-admin-pruebas";
    private static final String BOXES_CLAVE = "clave-boxes-pruebas";
    private static final String CRONO_CLAVE = "clave-crono-pruebas";
    private static final String MECANICO_CLAVE = "clave-mecanico-pruebas";

    @Autowired
    MockMvc mvc;

    private ResultActions enviar(org.springframework.test.web.servlet.RequestBuilder r) throws Exception {
        return mvc.perform(r);
    }

    @Test
    void lasOperacionesProtegidasExigenCredencialesYDevuelvenProblemDetails() throws Exception {
        enviar(get("/api/v1/clientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("No autenticado"));
        enviar(get("/api/v1/clientes").with(httpBasic("admin", "clave-equivocada"))).andExpect(status().isUnauthorized());
        enviar(get("/api/v1/clientes").with(httpBasic("desconocido", "clave-equivocada"))).andExpect(status().isUnauthorized());
        enviar(get("/api/v1/reportes/ocupacion")).andExpect(status().isUnauthorized());
        enviar(post("/api/v1/sesiones/T-X/iniciar")).andExpect(status().isUnauthorized());
        enviar(post("/api/v1/telemetria/pasos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoTransponder\":\"TR-101\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void cadaRolSoloAccedeAlTrabajoDeSuHistoriaDeUsuario() throws Exception {
        // HU-05: el reporte y las tarifas son del administrador
        enviar(get("/api/v1/reportes/ocupacion").with(httpBasic("admin", ADMIN_CLAVE))).andExpect(status().isOk());
        enviar(get("/api/v1/reportes/ocupacion").with(httpBasic("boxes", BOXES_CLAVE)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
        enviar(post("/api/v1/tarifas").with(httpBasic("boxes", BOXES_CLAVE)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipoKart\":\"ADULTO\",\"valorPorCupo\":50000}")).andExpect(status().isForbidden());
        enviar(put("/api/v1/flota/umbral").with(httpBasic("M-01", MECANICO_CLAVE)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"horas\":25}")).andExpect(status().isForbidden());

        // HU-02: la parrilla es de boxes; el piloto y el mecánico no la operan
        enviar(get("/api/v1/sesiones/T-X/parrilla").with(httpBasic("boxes", BOXES_CLAVE))).andExpect(status().isNotFound());
        enviar(get("/api/v1/sesiones/T-X/parrilla").with(httpBasic("M-01", MECANICO_CLAVE))).andExpect(status().isForbidden());
        enviar(get("/api/v1/clientes").with(httpBasic("boxes", BOXES_CLAVE))).andExpect(status().isOk());
        enviar(get("/api/v1/clientes").with(httpBasic("M-01", MECANICO_CLAVE))).andExpect(status().isForbidden());

        // HU-03: solo el decodificador (y el administrador) envían pasos por meta
        enviar(post("/api/v1/telemetria/pasos").with(httpBasic("boxes", BOXES_CLAVE))
                .contentType(MediaType.APPLICATION_JSON).content("{\"codigoTransponder\":\"TR-101\"}"))
                .andExpect(status().isForbidden());
        enviar(post("/api/v1/telemetria/pasos").with(httpBasic("cronometraje", CRONO_CLAVE))
                .contentType(MediaType.APPLICATION_JSON).content("{\"codigoTransponder\":\"TR-101\"}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }

    @Test
    void unMecanicoSoloCierraOrdenesEnSuPropioNombre() throws Exception {
        String cuerpoDeM02 = "{\"codigoMecanico\":\"M-02\",\"observaciones\":\"Cambio de bujía\"}";
        // RN-10: M-01 autenticado no puede firmar el cierre como M-02 (403), aunque el rol sea el correcto
        enviar(post("/api/v1/ordenes/OM-9999/cierre").with(httpBasic("M-01", MECANICO_CLAVE))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpoDeM02))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Un mecánico solo cierra órdenes en su propio nombre"));
        // M-02 sí llega al caso de uso (la orden no existe: 404)
        enviar(post("/api/v1/ordenes/OM-9999/cierre").with(httpBasic("M-02", MECANICO_CLAVE))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpoDeM02)).andExpect(status().isNotFound());
        // Asignar mecánicos es del administrador
        enviar(post("/api/v1/ordenes/OM-9999/asignacion").with(httpBasic("M-02", MECANICO_CLAVE))
                .contentType(MediaType.APPLICATION_JSON).content("{\"codigoMecanico\":\"M-02\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reservarConsultarYElWebhookNoNecesitanCuenta() throws Exception {
        enviar(get("/api/v1/sesiones")).andExpect(status().isOk());
        enviar(get("/api/v1/tarifas")).andExpect(status().isOk());
        enviar(get("/api/v1/reservas/" + java.util.UUID.randomUUID())).andExpect(status().isNotFound());
        // El webhook no usa credenciales: lo protege la firma HMAC, y sin firma responde 401 propio del dominio
        enviar(post("/api/v1/pagos/webhook").contentType(MediaType.APPLICATION_JSON)
                .content("{\"referencia\":\"KR-x\",\"aprobado\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Firma de webhook inválida"));
    }

    @Test
    void actuatorSoloExponeLaSaludAlPublico() throws Exception {
        enviar(get("/actuator/health")).andExpect(status().isOk());
        enviar(get("/actuator/info")).andExpect(status().isUnauthorized());
        enviar(get("/actuator/info").with(httpBasic("admin", ADMIN_CLAVE))).andExpect(status().isOk());
    }

    @Test
    void laPeticionPreviaDeCorsPermitePatchYLasCabecerasDelContrato() throws Exception {
        enviar(options("/api/v1/reservas/" + java.util.UUID.randomUUID())
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                        .contains("PATCH"));
    }

    @Test
    void swaggerPublicaLosCodigosDelContratoRestEnCadaOperacion() throws Exception {
        enviar(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ProblemaHttp.properties.detail").exists())
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"))
                // POST con cuerpo que crea: 201 y errores 400, 409, 500
                .andExpect(jsonPath("$.paths['/api/v1/reservas'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reservas'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reservas'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reservas'].post.responses['500']").exists())
                // Pago: 201 (intento nuevo) y 200 (clave repetida), más 404 por la ruta
                .andExpect(jsonPath("$.paths['/api/v1/reservas/{codigo}/pagos'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reservas/{codigo}/pagos'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reservas/{codigo}/pagos'].post.responses['404']").exists())
                // Cancelar: 200, 400 (estado inválido), 404 y 409
                .andExpect(jsonPath("$.paths['/api/v1/reservas/{codigo}'].patch.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reservas/{codigo}'].patch.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reservas/{codigo}'].patch.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reservas/{codigo}'].patch.responses['409']").exists())
                // Retirar un kart: 204, 404, 409
                .andExpect(jsonPath("$.paths['/api/v1/sesiones/{codigo}/asignaciones/{numeroKart}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/sesiones/{codigo}/asignaciones/{numeroKart}'].delete.responses['404']").exists())
                // Una consulta simple no declara 409
                .andExpect(jsonPath("$.paths['/api/v1/tarifas'].get.responses['409']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/tarifas'].get.responses['500']").exists())
                // Webhook: 401 por firma y 404 por referencia desconocida
                .andExpect(jsonPath("$.paths['/api/v1/pagos/webhook'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/pagos/webhook'].post.responses['404']").exists());
    }
}
