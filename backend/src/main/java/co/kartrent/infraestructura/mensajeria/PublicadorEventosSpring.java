package co.kartrent.infraestructura.mensajeria;

import co.kartrent.aplicacion.puertos.PublicadorEventos;
import co.kartrent.dominio.eventos.EventoDominio;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Indirección (GRASP): el dominio publica eventos sin conocer quién los escucha. */
@Component
public class PublicadorEventosSpring implements PublicadorEventos {
    private final ApplicationEventPublisher publisher;

    public PublicadorEventosSpring(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publicar(EventoDominio evento) {
        publisher.publishEvent(evento);
    }
}
