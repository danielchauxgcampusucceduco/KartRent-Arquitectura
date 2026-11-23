package co.kartrent.aplicacion.reservas;

import co.kartrent.aplicacion.puertos.PasarelaPagos;
import co.kartrent.aplicacion.puertos.RepositorioFacturacion;
import co.kartrent.aplicacion.puertos.UnidadDeTrabajo;
import co.kartrent.dominio.facturacion.Pago;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Compensación de cobros: devuelve el dinero de pagos aprobados que ya no corresponden a un servicio (reserva
 * cancelada, tanda cancelada o aprobación tardía). Se invoca siempre después de confirmar la transacción de base de
 * datos y fuera de ella: la llamada a la pasarela es externa. Mientras la devolución no se confirma, el pago queda
 * aprobado y sin reembolsar, lo que permite detectarlo y reintentarlo (la pasarela es idempotente por referencia).
 */
public class Reembolsos {
    private final PasarelaPagos pasarela;
    private final RepositorioFacturacion facturacion;
    private final UnidadDeTrabajo uow;

    public Reembolsos(PasarelaPagos pasarela, RepositorioFacturacion facturacion, UnidadDeTrabajo uow) {
        this.pasarela = pasarela;
        this.facturacion = facturacion;
        this.uow = uow;
    }

    /** Pagos aprobados de la reserva que todavía no se han devuelto. */
    public List<Pago> pendientesDe(UUID codigoReserva) {
        return facturacion.pagosDeReserva(codigoReserva).stream()
                .filter(p -> p.aprobado() && !p.reembolsado())
                .toList();
    }

    /** Devuelve el dinero en la pasarela y deja constancia en cada pago. Debe llamarse fuera de una transacción. */
    public List<Pago> devolver(List<Pago> pagos) {
        List<Pago> devueltos = new ArrayList<>();
        for (Pago pago : pagos) {
            pasarela.reembolsar(pago.referenciaPasarela(), pago.monto());
            Pago constancia = pago.reembolsar();
            uow.ejecutar(() -> facturacion.guardarPago(constancia));
            devueltos.add(constancia);
        }
        return devueltos;
    }
}
