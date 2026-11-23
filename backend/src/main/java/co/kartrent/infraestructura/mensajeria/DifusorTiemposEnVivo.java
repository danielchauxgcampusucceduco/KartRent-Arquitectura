package co.kartrent.infraestructura.mensajeria;

import co.kartrent.aplicacion.puertos.RepositorioSesiones;
import co.kartrent.dominio.eventos.MantenimientoRequerido;
import co.kartrent.dominio.eventos.SesionFinalizada;
import co.kartrent.dominio.eventos.VueltaRegistrada;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * HU-03: cuando se confirma una vuelta, empuja la clasificación por WebSocket (STOMP)
 * a /topic/sesiones/{codigo}/clasificacion. Se ejecuta después del commit.
 */
@Component
public class DifusorTiemposEnVivo {
    private final SimpMessagingTemplate stomp;
    private final RepositorioSesiones sesiones;

    public DifusorTiemposEnVivo(SimpMessagingTemplate stomp, RepositorioSesiones sesiones) {
        this.stomp = stomp;
        this.sesiones = sesiones;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void alRegistrarVuelta(VueltaRegistrada e) {
        sesiones.buscar(e.codigoSesion()).ifPresent(s -> stomp.convertAndSend(
                "/topic/sesiones/" + s.getCodigo() + "/clasificacion",
                Map.of("codigoSesion", s.getCodigo(), "estado", s.getEstado().name(), "ultimaVuelta", e,
                        "clasificacion", s.obtenerClasificacion())));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void alFinalizar(SesionFinalizada e) {
        stomp.convertAndSend("/topic/sesiones/" + e.codigoSesion() + "/estado", Map.of("estado", "FINALIZADA"));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void alRequerirMantenimiento(MantenimientoRequerido e) {
        stomp.convertAndSend("/topic/taller", e);
    }
}
