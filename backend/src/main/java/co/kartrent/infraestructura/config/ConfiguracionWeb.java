package co.kartrent.infraestructura.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS y documentación OpenAPI de la API REST. */
@Configuration
public class ConfiguracionWeb implements WebMvcConfigurer {
    private static final String PROBLEMA = "ProblemaHttp";
    private final String[] origenes;

    public ConfiguracionWeb(@Value("${kartrent.cors-origenes}") String[] origenes) {
        this.origenes = origenes;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOriginPatterns(origenes)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Idempotency-Key", "X-Firma");
    }

    @Bean
    OpenAPI openApi() {
        Schema<?> problema = new ObjectSchema()
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Conflicto con el estado actual"))
                .addProperty("status", new IntegerSchema().example(409))
                .addProperty("detail", new StringSchema().example("Cupos insuficientes"))
                .addProperty("instance", new StringSchema().example("/api/v1/reservas"))
                .description("Error en formato Problem Details (RFC 7807)");
        return new OpenAPI().info(new Info().title("KartRent API")
                        .description("Sistema Integral de Renta y Gestión Operativa de Karting — HU-01 a HU-05")
                        .version("1.0.0"))
                .components(new Components().addSchemas(PROBLEMA, problema)
                        .addSecuritySchemes("basicAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("basic").description("Personal del kartódromo: admin, boxes, cronometraje o "
                                        + "el código de un mecánico (M-01...). Los pilotos no necesitan credenciales.")));
    }

    /**
     * Documenta en cada operación los errores del contrato REST según lo que realmente puede fallar: 400 si recibe
     * cuerpo, parámetros o identificadores con formato (UUID, número); 404 si apunta a un recurso por la ruta; 409 si
     * cambia estado; 500 siempre. Las respuestas ya declaradas con @ApiResponse no se sobrescriben.
     */
    @Bean
    OperationCustomizer erroresDelContrato() {
        return (operacion, metodo) -> {
            boolean cuerpo = false;
            boolean parametros = false;
            boolean ruta = false;
            boolean formatoEstricto = false;
            for (MethodParameter p : metodo.getMethodParameters()) {
                cuerpo |= p.hasParameterAnnotation(RequestBody.class);
                parametros |= p.hasParameterAnnotation(RequestParam.class);
                if (p.hasParameterAnnotation(PathVariable.class)) {
                    ruta = true;
                    formatoEstricto |= p.getParameterType() != String.class;
                }
            }
            RequestMapping mapeo = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(), RequestMapping.class);
            boolean cambiaEstado = mapeo != null && mapeo.method().length > 0 && mapeo.method()[0] != RequestMethod.GET;
            if (cuerpo || parametros || formatoEstricto) {
                error(operacion, "400", "Datos inválidos o regla de negocio incumplida");
            }
            if (ruta) error(operacion, "404", "El recurso indicado no existe");
            if (cambiaEstado) error(operacion, "409", "Conflicto con el estado actual del recurso");
            error(operacion, "500", "Error interno inesperado");
            return operacion;
        };
    }

    private static void error(io.swagger.v3.oas.models.Operation operacion, String codigo, String descripcion) {
        if (operacion.getResponses().containsKey(codigo)) return;
        operacion.getResponses().addApiResponse(codigo, new ApiResponse().description(descripcion)
                .content(new Content().addMediaType("application/problem+json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEMA)))));
    }
}
