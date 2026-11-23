package co.kartrent.dominio.eventos;

import java.time.LocalDateTime;

public sealed interface EventoDominio permits ReservaConfirmada, SesionFinalizada, MantenimientoRequerido, VueltaRegistrada {
    LocalDateTime ocurrido();
}
