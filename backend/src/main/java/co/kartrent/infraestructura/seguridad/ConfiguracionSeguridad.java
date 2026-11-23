package co.kartrent.infraestructura.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;

/**
 * Seguridad mínima y explícita: HTTP Basic sin sesión, roles por historia de usuario y errores 401/403 en el mismo
 * formato Problem Details del resto de la API. Reservar, pagar, consultar tandas y el webhook (firmado con HMAC)
 * son públicos; todo lo operativo exige rol.
 */
@Configuration
@EnableWebSecurity
public class ConfiguracionSeguridad {
    private static final String API = "/api/v1";
    public static final String ADMIN = "ADMIN";
    public static final String BOXES = "BOXES";
    public static final String CRONOMETRAJE = "CRONOMETRAJE";
    public static final String MECANICO = "MECANICO";

    @Bean
    PasswordEncoder cifrador() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, ObjectMapper json) throws Exception {
        http.csrf(csrf -> csrf.disable()) // API sin sesión ni cookies: no hay sesión que falsificar
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basica -> basica.authenticationEntryPoint((req, res, e) ->
                        problema(json, res, HttpStatus.UNAUTHORIZED, "No autenticado",
                                "Se requieren credenciales válidas para este recurso")))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint((req, res, e) -> problema(json, res, HttpStatus.UNAUTHORIZED,
                                "No autenticado", "Se requieren credenciales válidas para este recurso"))
                        .accessDeniedHandler((req, res, e) -> problema(json, res, HttpStatus.FORBIDDEN,
                                "Acceso denegado", "Tu rol no permite esta operación")))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Documentación, salud y tiempos en vivo
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/ws", "/ws/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole(ADMIN)
                        // HU-01: el piloto reserva y paga sin cuenta; el webhook se autentica con la firma HMAC
                        .requestMatchers(HttpMethod.POST, API + "/clientes", API + "/reservas",
                                API + "/reservas/*/pagos", API + "/pagos/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, API + "/reservas/*", API + "/reservas/*/factura").permitAll()
                        .requestMatchers(HttpMethod.PATCH, API + "/reservas/*").permitAll()
                        // Consulta pública de la oferta y de la clasificación en vivo
                        .requestMatchers(HttpMethod.GET, API + "/tarifas", API + "/sesiones", API + "/sesiones/*",
                                API + "/sesiones/*/clasificacion").permitAll()
                        // Datos personales de los clientes: solo el personal que los necesita
                        .requestMatchers(HttpMethod.GET, API + "/clientes", API + "/clientes/**").hasAnyRole(ADMIN, BOXES)
                        // HU-02 boxes
                        .requestMatchers(HttpMethod.GET, API + "/sesiones/*/parrilla").hasAnyRole(BOXES, ADMIN)
                        .requestMatchers(HttpMethod.POST, API + "/sesiones/*/asignaciones", API + "/sesiones/*/iniciar",
                                API + "/sesiones/*/finalizar").hasAnyRole(BOXES, ADMIN)
                        .requestMatchers(HttpMethod.DELETE, API + "/sesiones/*/asignaciones/*").hasAnyRole(BOXES, ADMIN)
                        // HU-03 decodificador de transponders
                        .requestMatchers(HttpMethod.POST, API + "/telemetria/pasos").hasAnyRole(CRONOMETRAJE, ADMIN)
                        // HU-04 flota y taller (RN-10: el cierre además exige que el usuario sea el mecánico asignado)
                        .requestMatchers(HttpMethod.GET, API + "/flota/karts", API + "/flota/umbral", API + "/ordenes",
                                API + "/mecanicos").hasAnyRole(ADMIN, BOXES, MECANICO)
                        .requestMatchers(HttpMethod.POST, API + "/ordenes/*/cierre").hasAnyRole(MECANICO, ADMIN)
                        .requestMatchers(HttpMethod.POST, API + "/ordenes/*/asignacion").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PUT, API + "/flota/umbral").hasRole(ADMIN)
                        // HU-05 administración
                        .requestMatchers(HttpMethod.POST, API + "/tarifas", API + "/sesiones").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PATCH, API + "/sesiones/*").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, API + "/reportes/**").hasRole(ADMIN)
                        .anyRequest().authenticated());
        return http.build();
    }

    private static void problema(ObjectMapper json, HttpServletResponse res, HttpStatus estado, String titulo,
                                 String detalle) throws IOException {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(estado, detalle);
        p.setTitle(titulo);
        res.setStatus(estado.value());
        res.setContentType("application/problem+json");
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getWriter(), p);
    }
}
