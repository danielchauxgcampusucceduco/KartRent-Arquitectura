package co.kartrent.aplicacion.puertos;

import co.kartrent.dominio.facturacion.Factura;
import co.kartrent.dominio.facturacion.Pago;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioFacturacion {
    void guardarPago(Pago pago);
    Optional<Pago> pagoPorReferencia(String referencia);
    List<Pago> pagosDeReserva(UUID codigoReserva);
    void guardarFactura(Factura factura);
    Optional<Factura> facturaDeReserva(UUID codigoReserva);
    String siguienteNumeroFactura();
}
