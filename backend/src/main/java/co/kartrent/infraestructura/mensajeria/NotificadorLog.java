package co.kartrent.infraestructura.mensajeria;

import co.kartrent.aplicacion.puertos.Notificador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Adaptador de notificaciones para la demo: escribe en el log. En producción, correo/WhatsApp. */
@Component
public class NotificadorLog implements Notificador {
    private static final Logger log = LoggerFactory.getLogger(NotificadorLog.class);

    @Override
    public void enviar(String destino, String mensaje) {
        log.info("[NOTIFICACIÓN] para {}: {}", destino, mensaje);
    }
}
