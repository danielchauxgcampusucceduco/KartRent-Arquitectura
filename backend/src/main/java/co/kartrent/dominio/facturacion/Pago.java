package co.kartrent.dominio.facturacion;

import co.kartrent.dominio.comun.ConflictoException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Intento de cobro de una reserva. {@code reembolsado} indica que el dinero cobrado ya se devolvió. */
public record Pago(String referenciaPasarela, UUID codigoReserva, BigDecimal monto, MetodoPago metodo,
                   boolean aprobado, LocalDateTime fechaPago, boolean reembolsado) {

    public Pago(String referenciaPasarela, UUID codigoReserva, BigDecimal monto, MetodoPago metodo,
                boolean aprobado, LocalDateTime fechaPago) {
        this(referenciaPasarela, codigoReserva, monto, metodo, aprobado, fechaPago, false);
    }

    public boolean esAprobado() {
        return aprobado;
    }

    /** Un pago solo se reembolsa una vez y solo si la pasarela lo aprobó. */
    public Pago reembolsar() {
        if (!aprobado) throw new ConflictoException("Un pago rechazado no se reembolsa");
        if (reembolsado) throw new ConflictoException("El pago " + referenciaPasarela + " ya fue reembolsado");
        return new Pago(referenciaPasarela, codigoReserva, monto, metodo, true, fechaPago, true);
    }

    /** Pago que la pasarela aprobó de forma asíncrona (webhook). */
    public Pago aprobadoEn(LocalDateTime momento) {
        return new Pago(referenciaPasarela, codigoReserva, monto, metodo, true, momento, reembolsado);
    }
}
