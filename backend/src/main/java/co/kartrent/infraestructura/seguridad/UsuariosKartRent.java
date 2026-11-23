package co.kartrent.infraestructura.seguridad;

import co.kartrent.aplicacion.puertos.RepositorioMecanicos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Usuarios del personal del kartódromo, en memoria y con clave cifrada. Los roles salen de las historias de usuario:
 * <ul>
 *   <li>{@code admin} → ADMIN (HU-05: tarifas, tandas, umbral, reportes; también puede operar el resto)</li>
 *   <li>{@code boxes} → BOXES (HU-02: parrilla, asignación, inicio y fin de tanda)</li>
 *   <li>{@code cronometraje} → CRONOMETRAJE (HU-03: el decodificador que envía los pasos por meta)</li>
 *   <li>cada mecánico registrado (M-01, M-02...) → MECANICO (HU-04, RN-10: el usuario es su código de empleado)</li>
 * </ul>
 * Los pilotos no inician sesión: reservar y pagar son operaciones públicas y la reserva se identifica por su código.
 */
@Component
public class UsuariosKartRent implements UserDetailsService {
    private static final int LARGO_MINIMO = 8;
    private final Map<String, UserDetails> fijos;
    private final String claveMecanicos;
    private final RepositorioMecanicos mecanicos;

    public UsuariosKartRent(@Value("${kartrent.seguridad.admin-password}") String admin,
                            @Value("${kartrent.seguridad.boxes-password}") String boxes,
                            @Value("${kartrent.seguridad.cronometraje-password}") String cronometraje,
                            @Value("${kartrent.seguridad.mecanico-password}") String mecanico,
                            PasswordEncoder cifrador, RepositorioMecanicos mecanicos) {
        this.mecanicos = mecanicos;
        this.claveMecanicos = cifrador.encode(exigir("mecanico-password", mecanico));
        this.fijos = Map.of(
                "admin", usuario("admin", cifrador.encode(exigir("admin-password", admin)), "ADMIN"),
                "boxes", usuario("boxes", cifrador.encode(exigir("boxes-password", boxes)), "BOXES"),
                "cronometraje", usuario("cronometraje", cifrador.encode(exigir("cronometraje-password", cronometraje)),
                        "CRONOMETRAJE"));
    }

    @Override
    public UserDetails loadUserByUsername(String nombre) {
        UserDetails fijo = fijos.get(nombre);
        if (fijo != null) return fijo;
        return mecanicos.buscar(nombre).map(m -> usuario(m.getCodigoEmpleado(), claveMecanicos, "MECANICO"))
                .orElseThrow(() -> new UsernameNotFoundException("Usuario desconocido"));
    }

    private static UserDetails usuario(String nombre, String claveCifrada, String rol) {
        return User.withUsername(nombre).password(claveCifrada).roles(rol).build();
    }

    private static String exigir(String propiedad, String valor) {
        if (valor == null || valor.length() < LARGO_MINIMO) {
            throw new IllegalStateException("kartrent.seguridad." + propiedad + " debe tener al menos "
                    + LARGO_MINIMO + " caracteres");
        }
        return valor;
    }
}
