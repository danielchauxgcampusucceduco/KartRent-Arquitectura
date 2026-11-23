package co.kartrent.infraestructura.pagos;

import co.kartrent.aplicacion.puertos.PasarelaPagos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Adaptador de la pasarela (Fabricación Pura). Simula Wompi/PayU para la demo:
 * aprueba todo salvo el método "RECHAZAR". En producción se reemplaza por el cliente HTTP real.
 */
@Component
public class PasarelaPagosSimulada implements PasarelaPagos {
    private static final Logger log = LoggerFactory.getLogger(PasarelaPagosSimulada.class);

    @Override
    public ResultadoPago cobrar(String referencia, BigDecimal monto, String metodo) {
        boolean aprobado = !"RECHAZAR".equalsIgnoreCase(metodo) && monto.signum() > 0;
        log.info("Pasarela simulada: referencia={} monto={} metodo={} -> {}", referencia, monto, metodo,
                aprobado ? "APROBADA" : "RECHAZADA");
        return new ResultadoPago(aprobado, referencia, aprobado ? "APROBADA" : "RECHAZADA");
    }
}
