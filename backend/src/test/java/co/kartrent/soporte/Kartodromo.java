package co.kartrent.soporte;

import co.kartrent.aplicacion.administracion.ConfigurarOperacion;
import co.kartrent.aplicacion.administracion.ReporteOcupacion;
import co.kartrent.aplicacion.flota.ConsultarFlota;
import co.kartrent.aplicacion.flota.GestionarOrdenes;
import co.kartrent.aplicacion.flota.RegistrarUso;
import co.kartrent.aplicacion.pista.ControlarSesion;
import co.kartrent.aplicacion.pista.RegistrarPaso;
import co.kartrent.aplicacion.puertos.UnidadDeTrabajo;
import co.kartrent.aplicacion.reservas.ExpirarReservas;
import co.kartrent.aplicacion.reservas.PagarReserva;
import co.kartrent.aplicacion.reservas.RegistrarCliente;
import co.kartrent.aplicacion.reservas.ReservarTanda;

import java.time.Instant;
import java.time.ZoneId;

/** Raíz de composición para pruebas: todos los casos de uso cableados con adaptadores en memoria. */
public class Kartodromo {
    public final RelojAjustable reloj = new RelojAjustable(Instant.parse("2026-11-20T22:00:00Z"), ZoneId.of("America/Bogota"));
    public final Memoria.Clientes clientes = new Memoria.Clientes();
    public final Memoria.Reservas reservas = new Memoria.Reservas();
    public final Memoria.Sesiones sesiones = new Memoria.Sesiones();
    public final Memoria.Karts karts = new Memoria.Karts();
    public final Memoria.Mecanicos mecanicos = new Memoria.Mecanicos();
    public final Memoria.Ordenes ordenes = new Memoria.Ordenes();
    public final Memoria.Tarifas tarifas = new Memoria.Tarifas();
    public final Memoria.Facturacion facturacion = new Memoria.Facturacion();
    public final Memoria.Parametros parametros = new Memoria.Parametros();
    public final Memoria.PasarelaAprobadora pasarela = new Memoria.PasarelaAprobadora();
    public final Memoria.Buzon buzon = new Memoria.Buzon();
    private final UnidadDeTrabajo uow = UnidadDeTrabajo.DIRECTA;

    public final RegistrarCliente registrarCliente = new RegistrarCliente(clientes, uow, reloj);
    public final ReservarTanda reservarTanda = new ReservarTanda(clientes, sesiones, tarifas, reservas, parametros, uow, reloj);
    public final PagarReserva pagarReserva = new PagarReserva(reservas, clientes, facturacion, pasarela, buzon, buzon,
            parametros, uow, reloj);
    public final ExpirarReservas expirarReservas = new ExpirarReservas(reservas, sesiones, uow, reloj);
    public final ConsultarFlota consultarFlota = new ConsultarFlota(karts, parametros);
    public final RegistrarUso registrarUso = new RegistrarUso(karts, ordenes, parametros, buzon, buzon, reloj);
    public final GestionarOrdenes gestionarOrdenes = new GestionarOrdenes(ordenes, karts, mecanicos, uow, reloj);
    public final ControlarSesion controlarSesion = new ControlarSesion(sesiones, reservas, clientes, karts, consultarFlota,
            registrarUso, buzon, parametros, uow, reloj);
    public final RegistrarPaso registrarPaso = new RegistrarPaso(sesiones, parametros, buzon, uow, reloj);
    public final ConfigurarOperacion configurarOperacion = new ConfigurarOperacion(tarifas, sesiones, parametros, uow, reloj);
    public final ReporteOcupacion reporteOcupacion = new ReporteOcupacion(sesiones, reservas);
}
