package co.kartrent.dominio.facturacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Factura electrónica simplificada. El CUFE se calcula con SHA-384, el algoritmo que exige la DIAN;
 * en producción la firma la hace el proveedor tecnológico autorizado.
 */
public record Factura(String numero, String cufe, UUID codigoReserva, LocalDateTime fechaEmision,
                      BigDecimal subtotal, BigDecimal iva, BigDecimal total) {

    public static final BigDecimal TARIFA_IVA = new BigDecimal("0.19");

    /** El precio publicado incluye IVA; se desagrega base e impuesto. */
    public static Factura emitir(String numero, UUID codigoReserva, BigDecimal totalConIva, String nitEmisor,
                                 LocalDateTime ahora) {
        BigDecimal subtotal = totalConIva.divide(BigDecimal.ONE.add(TARIFA_IVA), 2, RoundingMode.HALF_UP);
        BigDecimal iva = totalConIva.subtract(subtotal);
        String cufe = sha384(numero + ahora + subtotal + iva + totalConIva + nitEmisor + codigoReserva);
        return new Factura(numero, cufe, codigoReserva, ahora, subtotal, iva, totalConIva);
    }

    public static BigDecimal calcularIva(BigDecimal base) {
        return base.multiply(TARIFA_IVA).setScale(2, RoundingMode.HALF_UP);
    }

    private static String sha384(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-384");
            return HexFormat.of().formatHex(md.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
