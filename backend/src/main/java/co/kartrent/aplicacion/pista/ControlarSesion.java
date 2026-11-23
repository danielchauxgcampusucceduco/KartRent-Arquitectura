package co.kartrent.aplicacion.pista;

import co.kartrent.aplicacion.flota.ConsultarFlota;
import co.kartrent.aplicacion.flota.RegistrarUso;
import co.kartrent.aplicacion.puertos.ParametrosOperacion;
import co.kartrent.aplicacion.puertos.PublicadorEventos;
import co.kartrent.aplicacion.puertos.RepositorioClientes;
import co.kartrent.aplicacion.puertos.RepositorioKarts;
import co.kartrent.aplicacion.puertos.RepositorioReservas;
import co.kartrent.aplicacion.puertos.RepositorioSesiones;
import co.kartrent.aplicacion.puertos.UnidadDeTrabajo;
import co.kartrent.dominio.comun.RecursoNoEncontradoException;
import co.kartrent.dominio.comun.ReglaNegocioException;
import co.kartrent.dominio.eventos.SesionFinalizada;
import co.kartrent.dominio.flota.Kart;
import co.kartrent.dominio.flota.OrdenMantenimiento;
import co.kartrent.dominio.pista.AsignacionKart;
import co.kartrent.dominio.pista.SesionPista;
import co.kartrent.dominio.pista.UsoKart;
import co.kartrent.dominio.reservas.Cliente;
import co.kartrent.dominio.reservas.EstadoReserva;
import co.kartrent.dominio.reservas.Reserva;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** HU-02 — interfaz ControlarSesion: parrilla, asignación, inicio y fin de la tanda. */
public class ControlarSesion {
    private final RepositorioSesiones sesiones;
    private final RepositorioReservas reservas;
    private final RepositorioClientes clientes;
    private final RepositorioKarts karts;
    private final ConsultarFlota flota;
    private final RegistrarUso registrarUso;
    private final PublicadorEventos eventos;
    private final ParametrosOperacion parametros;
    private final UnidadDeTrabajo uow;
    private final Clock reloj;

    public ControlarSesion(RepositorioSesiones sesiones, RepositorioReservas reservas, RepositorioClientes clientes,
                           RepositorioKarts karts, ConsultarFlota flota, RegistrarUso registrarUso,
                           PublicadorEventos eventos, ParametrosOperacion parametros, UnidadDeTrabajo uow, Clock reloj) {
        this.sesiones = sesiones;
        this.reservas = reservas;
        this.clientes = clientes;
        this.karts = karts;
        this.flota = flota;
        this.registrarUso = registrarUso;
        this.eventos = eventos;
        this.parametros = parametros;
        this.uow = uow;
        this.reloj = reloj;
    }

    public Parrilla parrilla(String codigoSesion) {
        SesionPista sesion = sesion(codigoSesion);
        List<Reserva> confirmadas = reservas.confirmadasPorSesion(codigoSesion);
        List<ReservaPendiente> pendientes = confirmadas.stream()
                .map(r -> new ReservaPendiente(r.getCodigo(), r.getDocumentoCliente(),
                        clientes.buscar(r.getDocumentoCliente()).map(Cliente::getNombre).orElse(r.getDocumentoCliente()),
                        r.getCantidadCupos(),
                        (int) sesion.getAsignaciones().stream().filter(a -> a.getCodigoReserva().equals(r.getCodigo())).count()))
                .toList();
        return new Parrilla(sesion, pendientes, flota.disponibles(sesion.getTipoKart()));
    }

    public AsignacionKart asignarKart(String codigoSesion, UUID codigoReserva, String documentoPiloto, int numeroKart) {
        return uow.ejecutar(() -> {
            SesionPista sesion = sesion(codigoSesion);
            Reserva reserva = reservas.buscar(codigoReserva)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Reserva " + codigoReserva + " no existe"));
            if (!reserva.getCodigoSesion().equals(codigoSesion) || reserva.getEstado() != EstadoReserva.CONFIRMADA) {
                throw new ReglaNegocioException("La reserva no está confirmada para esta tanda");
            }
            long usados = sesion.getAsignaciones().stream().filter(a -> a.getCodigoReserva().equals(codigoReserva)).count();
            if (usados >= reserva.getCantidadCupos()) throw new ReglaNegocioException("La reserva ya usó todos sus cupos");
            Cliente piloto = clientes.buscar(documentoPiloto)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Piloto " + documentoPiloto + " no registrado"));
            if (!piloto.isExoneracionFirmada()) throw new ReglaNegocioException("El piloto no ha firmado la exoneración");
            Kart kart = karts.buscar(numeroKart)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Kart " + numeroKart + " no existe"));
            AsignacionKart asignacion = sesion.asignarKart(codigoReserva, piloto.getDocumento(), piloto.getNombre(),
                    piloto.calcularEdad(LocalDate.now(reloj)), kart);
            kart.ponerEnPista();
            karts.guardar(kart);
            sesiones.guardar(sesion);
            return asignacion;
        });
    }

    public SesionPista iniciar(String codigoSesion) {
        return uow.ejecutar(() -> {
            SesionPista sesion = sesion(codigoSesion);
            sesion.iniciar(LocalDateTime.now(reloj));
            sesiones.guardar(sesion);
            return sesion;
        });
    }

    /** Al finalizar, Pista reporta el uso a Flota por la interfaz RegistrarUso (HU-02 → HU-04). */
    public Cierre finalizar(String codigoSesion) {
        return uow.ejecutar(() -> {
            SesionPista sesion = sesion(codigoSesion);
            LocalDateTime ahora = LocalDateTime.now(reloj);
            List<UsoKart> usos = sesion.finalizar(ahora, parametros.longitudPistaMetros());
            sesiones.guardar(sesion);
            List<OrdenMantenimiento> ordenes = registrarUso.ejecutar(usos);
            eventos.publicar(new SesionFinalizada(codigoSesion, ahora));
            return new Cierre(sesion, usos, ordenes);
        });
    }

    public List<SesionPista> listar() {
        return sesiones.listar();
    }

    public SesionPista sesion(String codigo) {
        return sesiones.buscar(codigo).orElseThrow(() -> new RecursoNoEncontradoException("Tanda " + codigo + " no existe"));
    }

    public record ReservaPendiente(UUID codigoReserva, String documentoCliente, String nombreCliente, int cupos, int asignados) {
    }

    public record Parrilla(SesionPista sesion, List<ReservaPendiente> reservas, List<Kart> kartsDisponibles) {
    }

    public record Cierre(SesionPista sesion, List<UsoKart> usos, List<OrdenMantenimiento> ordenesGeneradas) {
    }
}
