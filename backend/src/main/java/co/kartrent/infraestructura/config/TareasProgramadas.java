package co.kartrent.infraestructura.config;

import co.kartrent.aplicacion.reservas.ExpirarReservas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RN-01: libera los cupos de reservas cuyo tiempo de retención venció. */
@Component
public class TareasProgramadas {
    private static final Logger log = LoggerFactory.getLogger(TareasProgramadas.class);
    private final ExpirarReservas expirar;

    public TareasProgramadas(ExpirarReservas expirar) {
        this.expirar = expirar;
    }

    @Scheduled(fixedDelayString = "${kartrent.expiracion-ms}", initialDelay = 15000)
    public void expirarReservas() {
        int n = expirar.ejecutar();
        if (n > 0) log.info("Reservas expiradas y cupos liberados: {}", n);
    }
}
