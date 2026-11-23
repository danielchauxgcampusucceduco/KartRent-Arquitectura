package co.kartrent.dominio.eventos;

import java.time.LocalDateTime;

public record MantenimientoRequerido(int numeroKart, String numeroOrden, LocalDateTime ocurrido) implements EventoDominio {
}
