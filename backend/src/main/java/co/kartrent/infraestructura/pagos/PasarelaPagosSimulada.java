package co.kartrent.infraestructura.pagos;

import co.kartrent.aplicacion.puertos.PasarelaPagos;
import co.kartrent.dominio.comun.NoAutorizadoException;
import co.kartrent.dominio.comun.ReglaNegocioException;
import co.kartrent.dominio.facturacion.MetodoPago;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptador de la pasarela (Fabricación Pura). Simula una pasarela real para la demo: aprueba todo salvo el método
 * SIMULAR_RECHAZO. En producción se reemplaza por el cliente HTTP real y por su verificación de webhook.
 */
@Component
public class PasarelaPagosSimulada implements PasarelaPagos {
    private static final Logger log = LoggerFactory.getLogger(PasarelaPagosSimulada.class);
    private final VerificadorFirmaWebhook firma;
    private final ObjectMapper json;
    /** Referencias ya devueltas: reembolsar dos veces la misma no devuelve el dinero dos veces. */
    private final Set<String> reembolsadas = ConcurrentHashMap.newKeySet();

    public PasarelaPagosSimulada(VerificadorFirmaWebhook firma, ObjectMapper json) {
        this.firma = firma;
        this.json = json;
    }

    @Override
    public ResultadoPago cobrar(String referencia, BigDecimal monto, MetodoPago metodo) {
        boolean aprobado = metodo != MetodoPago.SIMULAR_RECHAZO && monto.signum() > 0;
        log.info("Pasarela simulada: referencia={} monto={} metodo={} -> {}", referencia, monto, metodo,
                aprobado ? "APROBADA" : "RECHAZADA");
        return new ResultadoPago(aprobado, referencia, aprobado ? "APROBADA" : "RECHAZADA");
    }

    @Override
    public void reembolsar(String referencia, BigDecimal monto) {
        if (reembolsadas.add(referencia)) {
            log.info("Pasarela simulada: reembolso referencia={} monto={}", referencia, monto);
        }
    }

    @Override
    public NotificacionPago interpretarNotificacion(String cuerpo, String firmaRecibida) {
        if (!firma.esValida(cuerpo, firmaRecibida)) {
            throw new NoAutorizadoException("Firma de webhook inválida");
        }
        try {
            NotificacionPago n = json.readValue(cuerpo, NotificacionPago.class);
            if (n.referencia() == null || n.referencia().isBlank()) {
                throw new ReglaNegocioException("La notificación no trae referencia");
            }
            return n;
        } catch (JsonProcessingException e) {
            throw new ReglaNegocioException("El cuerpo del webhook no es un JSON válido");
        }
    }
}
