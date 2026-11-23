package co.kartrent.aplicacion.puertos;

import java.math.BigDecimal;

/** Variaciones Protegidas: cambiar de proveedor de pagos solo cambia el adaptador. */
public interface PasarelaPagos {
    ResultadoPago cobrar(String referencia, BigDecimal monto, String metodo);

    record ResultadoPago(boolean aprobado, String referenciaPasarela, String mensaje) {
    }
}
