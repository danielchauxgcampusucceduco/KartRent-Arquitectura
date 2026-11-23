package co.kartrent.dominio.eventos;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReservaConfirmada(UUID codigoReserva, String documentoCliente, String codigoSesion, LocalDateTime ocurrido)
        implements EventoDominio {
}
