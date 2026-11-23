package co.kartrent.aplicacion.puertos;

import co.kartrent.dominio.facturacion.MetodoPago;

import java.math.BigDecimal;

/**
 * Variaciones Protegidas: cambiar de proveedor de pagos solo cambia el adaptador. Cada pasarela define su propio
 * formato y firma de webhook, por eso el puerto también sabe interpretar la notificación entrante.
 */
public interface PasarelaPagos {
    ResultadoPago cobrar(String referencia, BigDecimal monto, MetodoPago metodo);

    /**
     * Devuelve a la tarjeta o cuenta el dinero de un cobro aprobado. Idempotente por referencia: repetir la llamada
     * con la misma referencia no devuelve el dinero dos veces.
     */
    void reembolsar(String referencia, BigDecimal monto);

    /**
     * Verifica la firma y lee el cuerpo del webhook.
     *
     * @throws co.kartrent.dominio.comun.NoAutorizadoException si la firma no coincide (401)
     * @throws co.kartrent.dominio.comun.ReglaNegocioException si el cuerpo no es una notificación válida (400)
     */
    NotificacionPago interpretarNotificacion(String cuerpo, String firma);

    record ResultadoPago(boolean aprobado, String referenciaPasarela, String mensaje) {
    }

    record NotificacionPago(String referencia, boolean aprobado) {
    }
}
