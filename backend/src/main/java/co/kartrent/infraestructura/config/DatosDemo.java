package co.kartrent.infraestructura.config;

import co.kartrent.aplicacion.administracion.ConfigurarOperacion;
import co.kartrent.aplicacion.puertos.RepositorioSesiones;
import co.kartrent.dominio.flota.TipoKart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Programa la parrilla de tandas de hoy y los próximos dos días para que la demo siempre tenga
 * tandas abiertas. Se desactiva con kartrent.demo=false.
 */
@Component
@ConditionalOnProperty(name = "kartrent.demo", havingValue = "true", matchIfMissing = true)
public class DatosDemo implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DatosDemo.class);
    private static final DateTimeFormatter CODIGO = DateTimeFormatter.ofPattern("MMdd-HHmm");
    private final ConfigurarOperacion configurar;
    private final RepositorioSesiones sesiones;
    private final Clock reloj;

    public DatosDemo(ConfigurarOperacion configurar, RepositorioSesiones sesiones, Clock reloj) {
        this.configurar = configurar;
        this.sesiones = sesiones;
        this.reloj = reloj;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        int creadas = 0;
        for (int d = 0; d < 3; d++) {
            LocalDate dia = ahora.toLocalDate().plusDays(d);
            for (int hora = 10; hora <= 21; hora++) {
                LocalDateTime inicio = dia.atTime(hora, 0);
                if (inicio.isBefore(ahora)) continue;
                boolean infantil = hora == 10 || hora == 14 || hora == 16;
                String codigo = (infantil ? "I-" : "T-") + inicio.format(CODIGO);
                if (sesiones.buscar(codigo).isPresent()) continue;
                configurar.programarTanda(codigo, inicio, 10, infantil ? 4 : 8,
                        infantil ? TipoKart.INFANTIL : TipoKart.ADULTO);
                creadas++;
            }
        }
        log.info("Datos de demo: {} tandas programadas", creadas);
    }
}
