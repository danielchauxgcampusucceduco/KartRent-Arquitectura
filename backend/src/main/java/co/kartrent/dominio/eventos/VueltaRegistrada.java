package co.kartrent.dominio.eventos;

import java.time.LocalDateTime;

public record VueltaRegistrada(String codigoSesion, int numeroKart, int numeroVuelta, long tiempoMs, LocalDateTime ocurrido)
        implements EventoDominio {
}
