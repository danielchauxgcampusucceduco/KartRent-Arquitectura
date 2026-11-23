package co.kartrent.dominio.eventos;

import java.time.LocalDateTime;

public record SesionFinalizada(String codigoSesion, LocalDateTime ocurrido) implements EventoDominio {
}
