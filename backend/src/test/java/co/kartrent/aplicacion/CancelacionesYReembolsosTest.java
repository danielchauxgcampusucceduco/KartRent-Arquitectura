package co.kartrent.aplicacion;

import co.kartrent.aplicacion.reservas.PagarReserva;
import co.kartrent.aplicacion.reservas.RegistrarCliente;
import co.kartrent.dominio.comun.ConflictoException;
import co.kartrent.dominio.comun.RecursoNoEncontradoException;
import co.kartrent.dominio.facturacion.MetodoPago;
import co.kartrent.dominio.facturacion.Pago;
import co.kartrent.dominio.flota.EstadoKart;
import co.kartrent.dominio.flota.KartAdulto;
import co.kartrent.dominio.flota.TipoKart;
import co.kartrent.dominio.pista.EstadoSesion;
import co.kartrent.dominio.reservas.EstadoReserva;
import co.kartrent.dominio.reservas.Reserva;
import co.kartrent.soporte.Kartodromo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Casos de uso de cancelación, reintento de pago y reembolso, con adaptadores en memoria. */
class CancelacionesYReembolsosTest {
    private Kartodromo k;

    @BeforeEach
    void preparar() {
        k = new Kartodromo();
        k.configurarOperacion.registrarTarifa(TipoKart.ADULTO, BigDecimal.valueOf(45000), LocalDate.of(2026, 1, 1));
        k.configurarOperacion.programarTanda("T-1", LocalDateTime.now(k.reloj).plusHours(2), 10, 4, TipoKart.ADULTO);
        for (String doc : new String[]{"1001", "1002"}) {
            k.registrarCliente.ejecutar(new RegistrarCliente.Comando(doc, "Piloto " + doc, doc + "@correo.co", "300",
                    LocalDate.now(k.reloj).minusYears(25), true));
        }
        for (int i = 1; i <= 3; i++) {
            k.karts.guardar(new KartAdulto(i, EstadoKart.DISPONIBLE, 0, 100, 5, "TR-" + i));
        }
    }

    private Reserva reservar(String documento, int cupos) {
        return k.reservarTanda.ejecutar(documento, "T-1", cupos);
    }

    @Test
    void unPagoRechazadoSeReintentaSinClaveYLuegoSeApruebaSinDobleCobro() {
        Reserva r = reservar("1001", 1);
        k.pasarela.aprobar = false;
        PagarReserva.Resultado rechazado = k.pagarReserva.ejecutar(r.getCodigo(), MetodoPago.TARJETA, null);
        assertFalse(rechazado.pago().aprobado());
        assertEquals(EstadoReserva.PENDIENTE_PAGO, k.reservas.buscar(r.getCodigo()).orElseThrow().getEstado());

        k.pasarela.aprobar = true;
        PagarReserva.Resultado aprobado = k.pagarReserva.ejecutar(r.getCodigo(), MetodoPago.PSE, null);
        assertTrue(aprobado.pago().aprobado());
        assertFalse(aprobado.repeticion());
        assertEquals(EstadoReserva.CONFIRMADA, aprobado.reserva().getEstado());
        assertEquals(2, k.pasarela.cobros);

        PagarReserva.Resultado repetido = k.pagarReserva.ejecutar(r.getCodigo(), MetodoPago.PSE, null);
        assertTrue(repetido.repeticion());
        assertEquals(2, k.pasarela.cobros, "un pago aprobado nunca se cobra de nuevo");
    }

    @Test
    void unaClaveExplicitaRepiteSuResultadoAunqueHayaSidoRechazado() {
        Reserva r = reservar("1001", 1);
        k.pasarela.aprobar = false;
        k.pagarReserva.ejecutar(r.getCodigo(), MetodoPago.TARJETA, "clave-a");
        PagarReserva.Resultado repetido = k.pagarReserva.ejecutar(r.getCodigo(), MetodoPago.TARJETA, "clave-a");
        assertTrue(repetido.repeticion());
        assertFalse(repetido.pago().aprobado());
        assertEquals(1, k.pasarela.cobros);
    }

    @Test
    void laMismaClaveNoSePuedeUsarEnOtraReserva() {
        Reserva a = reservar("1001", 1);
        Reserva b = reservar("1002", 1);
        k.pagarReserva.ejecutar(a.getCodigo(), MetodoPago.TARJETA, "clave-compartida");
        assertThrows(ConflictoException.class,
                () -> k.pagarReserva.ejecutar(b.getCodigo(), MetodoPago.TARJETA, "clave-compartida"));
        assertEquals(1, k.pasarela.cobros);
    }

    @Test
    void noSeCobraUnaReservaCuyaRetencionYaExpiro() {
        Reserva r = reservar("1001", 1);
        k.reloj.avanzar(Duration.ofMinutes(11));
        assertThrows(ConflictoException.class, () -> k.pagarReserva.ejecutar(r.getCodigo(), MetodoPago.TARJETA, "x"));
        assertEquals(0, k.pasarela.cobros);
    }

    @Test
    void laAprobacionAsincronaATiempoConfirmaYFactura() {
        Reserva r = reservar("1001", 1);
        k.pasarela.aprobar = false;
        k.pagarReserva.ejecutar(r.getCodigo(), MetodoPago.TARJETA, "k-3ds");
        PagarReserva.Resultado confirmado = k.pagarReserva.confirmarDesdePasarela("KR-k-3ds", true);
        assertEquals(EstadoReserva.CONFIRMADA, confirmado.reserva().getEstado());
        assertNotNull(confirmado.factura());
        assertTrue(k.pasarela.reembolsos.isEmpty());
    }

    @Test
    void laAprobacionAsincronaTardiaSeReembolsaEnLugarDeIgnorarse() {
        Reserva r = reservar("1001", 2);
        k.pasarela.aprobar = false;
        k.pagarReserva.ejecutar(r.getCodigo(), MetodoPago.TARJETA, "k-tarde");
        k.reloj.avanzar(Duration.ofMinutes(11));

        PagarReserva.Resultado tardio = k.pagarReserva.confirmarDesdePasarela("KR-k-tarde", true);
        assertTrue(tardio.pago().aprobado());
        assertTrue(tardio.pago().reembolsado());
        assertNull(tardio.factura());
        assertEquals(List.of("KR-k-tarde"), k.pasarela.reembolsos);
        assertTrue(k.facturacion.pagoPorReferencia("KR-k-tarde").orElseThrow().reembolsado());

        // El aviso repetido no devuelve el dinero dos veces, y el barrido de expiración libera los cupos.
        k.pagarReserva.confirmarDesdePasarela("KR-k-tarde", true);
        assertEquals(1, k.pasarela.reembolsos.size());
        k.expirarReservas.ejecutar();
        assertEquals(EstadoReserva.EXPIRADA, k.reservas.buscar(r.getCodigo()).orElseThrow().getEstado());
        assertEquals(4, k.sesiones.buscar("T-1").orElseThrow().cuposDisponibles());
    }

    @Test
    void cancelarUnaReservaInexistenteEsNoEncontrada() {
        assertThrows(RecursoNoEncontradoException.class, () -> k.cancelarReserva.ejecutar(UUID.randomUUID()));
    }

    @Test
    void unaReservaYaCanceladaOExpiradaNoSeCancelaDeNuevo() {
        Reserva cancelada = reservar("1001", 1);
        k.cancelarReserva.ejecutar(cancelada.getCodigo());
        assertThrows(ConflictoException.class, () -> k.cancelarReserva.ejecutar(cancelada.getCodigo()));

        Reserva expirada = reservar("1002", 1);
        k.reloj.avanzar(Duration.ofMinutes(11));
        k.expirarReservas.ejecutar();
        assertThrows(ConflictoException.class, () -> k.cancelarReserva.ejecutar(expirada.getCodigo()));
        assertEquals(4, k.sesiones.buscar("T-1").orElseThrow().cuposDisponibles());
    }

    @Test
    void cancelarUnaReservaPagadaDevuelveElDineroUnaSolaVez() {
        Reserva r = reservar("1001", 2);
        k.pagarReserva.ejecutar(r.getCodigo(), MetodoPago.TARJETA, "pago");
        Reserva cancelada = k.cancelarReserva.ejecutar(r.getCodigo());
        assertEquals(EstadoReserva.REEMBOLSADA, cancelada.getEstado());
        assertEquals(List.of("KR-pago"), k.pasarela.reembolsos);
        Pago pago = k.facturacion.pagoPorReferencia("KR-pago").orElseThrow();
        assertTrue(pago.aprobado() && pago.reembolsado());
        assertThrows(ConflictoException.class, () -> k.cancelarReserva.ejecutar(r.getCodigo()));
        assertEquals(1, k.pasarela.reembolsos.size());
    }

    @Test
    void lasAsignacionesNuevasNuncaRepitenElNumeroDeParrillaDespuesDeRetirarUnKart() {
        Reserva a = reservar("1001", 1);
        Reserva b = reservar("1002", 1);
        k.pagarReserva.ejecutar(a.getCodigo(), MetodoPago.TARJETA, "a");
        k.pagarReserva.ejecutar(b.getCodigo(), MetodoPago.TARJETA, "b");
        k.controlarSesion.asignarKart("T-1", a.getCodigo(), "1001", 1);
        k.controlarSesion.asignarKart("T-1", b.getCodigo(), "1002", 2);
        k.controlarSesion.desasignarKart("T-1", 1);
        assertEquals(EstadoKart.DISPONIBLE, k.karts.buscar(1).orElseThrow().getEstado());

        k.controlarSesion.asignarKart("T-1", a.getCodigo(), "1001", 3);
        var parrilla = k.sesiones.buscar("T-1").orElseThrow().getAsignaciones();
        assertEquals(List.of(2, 3), parrilla.stream().map(x -> x.getNumeroParrilla()).toList());
    }

    @Test
    void retirarUnKartQueNoEstaEnLaParrillaEsNoEncontrado() {
        Reserva a = reservar("1001", 1);
        k.pagarReserva.ejecutar(a.getCodigo(), MetodoPago.TARJETA, "a");
        k.controlarSesion.asignarKart("T-1", a.getCodigo(), "1001", 1);
        assertThrows(RecursoNoEncontradoException.class, () -> k.controlarSesion.desasignarKart("T-1", 2));
    }

    @Test
    void cancelarUnaTandaLiberaLosKartsReembolsaYAvisaALosClientes() {
        Reserva pagada = reservar("1001", 1);
        Reserva pendiente = reservar("1002", 1);
        k.pagarReserva.ejecutar(pagada.getCodigo(), MetodoPago.TARJETA, "p");
        k.controlarSesion.asignarKart("T-1", pagada.getCodigo(), "1001", 2);

        var sesion = k.cancelarTanda.ejecutar("T-1");
        assertEquals(EstadoSesion.CANCELADA, sesion.getEstado());
        assertEquals(0, sesion.getAsignaciones().size());
        assertEquals(EstadoKart.DISPONIBLE, k.karts.buscar(2).orElseThrow().getEstado());
        assertEquals(EstadoReserva.REEMBOLSADA, k.reservas.buscar(pagada.getCodigo()).orElseThrow().getEstado());
        assertEquals(EstadoReserva.CANCELADA, k.reservas.buscar(pendiente.getCodigo()).orElseThrow().getEstado());
        assertEquals(List.of("KR-p"), k.pasarela.reembolsos);
        assertTrue(k.buzon.mensajes.stream().anyMatch(m -> m.startsWith("1001@correo.co") && m.contains("cancelada")));
        assertEquals(0, k.reporteOcupacion.ejecutar(LocalDate.now(k.reloj).minusDays(1), LocalDate.now(k.reloj).plusDays(1))
                .tandas().size(), "una tanda cancelada no cuenta en la ocupación");
        assertThrows(ConflictoException.class, () -> reservar("1001", 1));
    }

    @Test
    void unaTandaEnCursoNoSePuedeCancelar() {
        Reserva a = reservar("1001", 1);
        k.pagarReserva.ejecutar(a.getCodigo(), MetodoPago.TARJETA, "a");
        k.controlarSesion.asignarKart("T-1", a.getCodigo(), "1001", 1);
        k.controlarSesion.iniciar("T-1");
        assertThrows(ConflictoException.class, () -> k.cancelarTanda.ejecutar("T-1"));
        assertEquals(EstadoSesion.EN_CURSO, k.sesiones.buscar("T-1").orElseThrow().getEstado());
        assertEquals(EstadoReserva.CONFIRMADA, k.reservas.buscar(a.getCodigo()).orElseThrow().getEstado());
    }
}
