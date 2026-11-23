package co.kartrent.aplicacion.administracion;

import co.kartrent.aplicacion.puertos.Notificador;
import co.kartrent.aplicacion.puertos.RepositorioClientes;
import co.kartrent.aplicacion.puertos.RepositorioKarts;
import co.kartrent.aplicacion.puertos.RepositorioReservas;
import co.kartrent.aplicacion.puertos.RepositorioSesiones;
import co.kartrent.aplicacion.puertos.UnidadDeTrabajo;
import co.kartrent.aplicacion.reservas.Reembolsos;
import co.kartrent.dominio.comun.RecursoNoEncontradoException;
import co.kartrent.dominio.facturacion.Pago;
import co.kartrent.dominio.pista.SesionPista;
import co.kartrent.dominio.reservas.EstadoReserva;
import co.kartrent.dominio.reservas.Reserva;

import java.util.ArrayList;
import java.util.List;

/**
 * HU-05 — el administrador cancela una tanda que no ha iniciado: se liberan los karts de la parrilla, las reservas
 * pendientes se cancelan y las pagadas se reembolsan. Se avisa a los clientes cuando el cambio ya está confirmado.
 */
public class CancelarTanda {
    private final RepositorioSesiones sesiones;
    private final RepositorioReservas reservas;
    private final RepositorioKarts karts;
    private final RepositorioClientes clientes;
    private final Reembolsos reembolsos;
    private final Notificador notificador;
    private final UnidadDeTrabajo uow;

    public CancelarTanda(RepositorioSesiones sesiones, RepositorioReservas reservas, RepositorioKarts karts,
                         RepositorioClientes clientes, Reembolsos reembolsos, Notificador notificador,
                         UnidadDeTrabajo uow) {
        this.sesiones = sesiones;
        this.reservas = reservas;
        this.karts = karts;
        this.clientes = clientes;
        this.reembolsos = reembolsos;
        this.notificador = notificador;
        this.uow = uow;
    }

    public SesionPista ejecutar(String codigoSesion) {
        Cancelacion cancelacion = uow.ejecutarConReintentos(() -> {
            SesionPista sesion = sesiones.buscar(codigoSesion)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Tanda " + codigoSesion + " no existe"));
            sesion.cancelar().forEach(a -> karts.buscar(a.getNumeroKart()).ifPresent(k -> {
                k.liberar();
                karts.guardar(k);
            }));
            List<Pago> aDevolver = new ArrayList<>();
            List<Reserva> avisar = new ArrayList<>();
            for (Reserva r : reservas.porSesion(codigoSesion)) {
                if (r.getEstado() == EstadoReserva.PENDIENTE_PAGO) {
                    r.cancelar();
                } else if (r.getEstado() == EstadoReserva.CONFIRMADA) {
                    r.reembolsar();
                    aDevolver.addAll(reembolsos.pendientesDe(r.getCodigo()));
                    avisar.add(r);
                } else {
                    continue;
                }
                reservas.guardar(r);
            }
            sesiones.guardar(sesion);
            return new Cancelacion(sesion, aDevolver, avisar);
        });
        cancelacion.avisar().forEach(r -> clientes.buscar(r.getDocumentoCliente()).ifPresent(c -> notificador.enviar(
                c.getCorreo() != null ? c.getCorreo() : c.getDocumento(),
                "La tanda " + codigoSesion + " fue cancelada. Tu pago de " + r.getValorTotal() + " será reembolsado.")));
        reembolsos.devolver(cancelacion.aDevolver());
        return cancelacion.sesion();
    }

    private record Cancelacion(SesionPista sesion, List<Pago> aDevolver, List<Reserva> avisar) {
    }
}
