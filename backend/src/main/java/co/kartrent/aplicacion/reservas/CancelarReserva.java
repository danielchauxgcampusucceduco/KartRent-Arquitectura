package co.kartrent.aplicacion.reservas;

import co.kartrent.aplicacion.puertos.RepositorioReservas;
import co.kartrent.aplicacion.puertos.RepositorioSesiones;
import co.kartrent.aplicacion.puertos.UnidadDeTrabajo;
import co.kartrent.dominio.comun.ConflictoException;
import co.kartrent.dominio.comun.RecursoNoEncontradoException;
import co.kartrent.dominio.facturacion.Pago;
import co.kartrent.dominio.pista.SesionPista;
import co.kartrent.dominio.reservas.Reserva;

import java.util.List;
import java.util.UUID;

/**
 * HU-01 — cancelación de una reserva. Pendiente de pago: se libera la retención. Confirmada: se devuelve el dinero
 * (reembolso) siempre que su piloto aún no tenga kart asignado y la tanda no haya comenzado.
 */
public class CancelarReserva {
    private final RepositorioReservas reservas;
    private final RepositorioSesiones sesiones;
    private final Reembolsos reembolsos;
    private final UnidadDeTrabajo uow;

    public CancelarReserva(RepositorioReservas reservas, RepositorioSesiones sesiones, Reembolsos reembolsos,
                           UnidadDeTrabajo uow) {
        this.reservas = reservas;
        this.sesiones = sesiones;
        this.reembolsos = reembolsos;
        this.uow = uow;
    }

    public Reserva ejecutar(UUID codigoReserva) {
        Cancelacion cancelacion = uow.ejecutarConReintentos(() -> {
            Reserva reserva = reservas.buscar(codigoReserva)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Reserva " + codigoReserva + " no existe"));
            boolean pagada = switch (reserva.getEstado()) {
                case PENDIENTE_PAGO -> false;
                case CONFIRMADA -> true;
                default -> throw new ConflictoException("La reserva ya está " + reserva.getEstado());
            };
            SesionPista sesion = sesiones.buscar(reserva.getCodigoSesion()).orElseThrow(
                    () -> new RecursoNoEncontradoException("Tanda " + reserva.getCodigoSesion() + " no existe"));
            sesion.liberarCuposDeReserva(codigoReserva, reserva.getCantidadCupos());
            List<Pago> aDevolver = List.of();
            if (pagada) {
                reserva.reembolsar();
                aDevolver = reembolsos.pendientesDe(codigoReserva);
            } else {
                reserva.cancelar();
            }
            reservas.guardar(reserva);
            sesiones.guardar(sesion);
            return new Cancelacion(reserva, aDevolver);
        });
        reembolsos.devolver(cancelacion.aDevolver());
        return cancelacion.reserva();
    }

    private record Cancelacion(Reserva reserva, List<Pago> aDevolver) {
    }
}
