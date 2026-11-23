package co.kartrent.dominio.facturacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record Pago(String referenciaPasarela, UUID codigoReserva, BigDecimal monto, String metodo,
                   boolean aprobado, LocalDateTime fechaPago) {
    public boolean esAprobado() {
        return aprobado;
    }
}
