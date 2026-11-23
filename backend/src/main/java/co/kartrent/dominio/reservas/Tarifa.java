package co.kartrent.dominio.reservas;

import co.kartrent.dominio.comun.ReglaNegocioException;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Precio por cupo de un tipo de kart, vigente desde una fecha (HU-05). */
public record Tarifa(String tipoKart, BigDecimal valorPorCupo, LocalDate vigenteDesde) {
    public Tarifa {
        if (valorPorCupo == null || valorPorCupo.signum() <= 0)
            throw new ReglaNegocioException("La tarifa debe ser mayor que cero");
    }

    public boolean estaVigente(LocalDate fecha) {
        return !fecha.isBefore(vigenteDesde);
    }
}
