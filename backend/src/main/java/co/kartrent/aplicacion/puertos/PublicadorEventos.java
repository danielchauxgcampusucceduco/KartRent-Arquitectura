package co.kartrent.aplicacion.puertos;

import co.kartrent.dominio.eventos.EventoDominio;

public interface PublicadorEventos {
    void publicar(EventoDominio evento);
}
