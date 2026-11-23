package co.kartrent.infraestructura.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS y documentación OpenAPI de la API REST. */
@Configuration
public class ConfiguracionWeb implements WebMvcConfigurer {
    private final String[] origenes;

    public ConfiguracionWeb(@Value("${kartrent.cors-origenes}") String[] origenes) {
        this.origenes = origenes;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOriginPatterns(origenes).allowedMethods("GET", "POST", "PUT", "DELETE");
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info().title("KartRent API")
                .description("Sistema Integral de Renta y Gestión Operativa de Karting — HU-01 a HU-05")
                .version("1.0.0"));
    }
}
