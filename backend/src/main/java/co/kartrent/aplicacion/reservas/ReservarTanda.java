package co.kartrent.aplicacion.reservas;

import co.kartrent.aplicacion.puertos.ParametrosOperacion;
import co.kartrent.aplicacion.puertos.RepositorioClientes;
import co.kartrent.aplicacion.puertos.RepositorioReservas;
import co.kartrent.aplicacion.puertos.RepositorioSesiones;
import co.kartrent.aplicacion.puertos.RepositorioTarifas;
import co.kartrent.aplicacion.puertos.UnidadDeTrabajo;
import co.kartrent.dominio.comun.RecursoNoEncontradoException;
import co.kartrent.dominio.comun.ConflictoException;
import co.kartrent.dominio.pista.SesionPista;
import co.kartrent.dominio.reservas.Cliente;
import co.kartrent.dominio.reservas.Reserva;
import co.kartrent.dominio.reservas.Tarifa;

import java.time.Clock;
import java.time.LocalDateTime;

/** HU-01 — Controlador (GRASP): coordina, las reglas viven en el dominio. */
public class ReservarTanda {
    private final RepositorioClientes clientes;
    private final RepositorioSesiones sesiones;
    private final RepositorioTarifas tarifas;
    private final RepositorioReservas reservas;
    private final ParametrosOperacion parametros;
    private final UnidadDeTrabajo uow;
    private final Clock reloj;

    public ReservarTanda(RepositorioClientes clientes, RepositorioSesiones sesiones, RepositorioTarifas tarifas,
                         RepositorioReservas reservas, ParametrosOperacion parametros, UnidadDeTrabajo uow, Clock reloj) {
        this.clientes = clientes;
        this.sesiones = sesiones;
        this.tarifas = tarifas;
        this.reservas = reservas;
        this.parametros = parametros;
        this.uow = uow;
        this.reloj = reloj;
    }

    public Reserva ejecutar(String documentoCliente, String codigoSesion, int cupos) {
        return uow.ejecutarConReintentos(() -> {
            Cliente cliente = clientes.buscar(documentoCliente)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Cliente " + documentoCliente + " no registrado"));
            SesionPista sesion = sesiones.buscar(codigoSesion)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Tanda " + codigoSesion + " no existe"));
            LocalDateTime ahora = LocalDateTime.now(reloj);
            Tarifa tarifa = tarifas.vigente(sesion.getTipoKart(), ahora.toLocalDate())
                    .orElseThrow(() -> new ConflictoException("No hay tarifa vigente para karts " + sesion.getTipoKart()));
            Reserva reserva = Reserva.crear(cliente, sesion, cupos, tarifa, ahora, parametros.retencionReserva());
            sesiones.guardar(sesion);
            reservas.guardar(reserva);
            return reserva;
        });
    }
}
