package co.kartrent.dominio;

import co.kartrent.dominio.comun.ConflictoException;
import co.kartrent.dominio.comun.ReglaNegocioException;
import co.kartrent.dominio.facturacion.Factura;
import co.kartrent.dominio.flota.EstadoKart;
import co.kartrent.dominio.flota.KartAdulto;
import co.kartrent.dominio.flota.KartInfantil;
import co.kartrent.dominio.flota.TipoKart;
import co.kartrent.dominio.pista.AsignacionKart;
import co.kartrent.dominio.pista.SesionPista;
import co.kartrent.dominio.pista.VueltaTelemetria;
import co.kartrent.dominio.reservas.Cliente;
import co.kartrent.dominio.reservas.Reserva;
import co.kartrent.dominio.reservas.Tarifa;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReglasDominioTest {

    @Test
    void kartInfantilExigeOchoAnosYKartAdultoDieciseis() {
        assertEquals(8, new KartInfantil(9, EstadoKart.DISPONIBLE, 0, 0, 0, "TR-109").edadMinimaPiloto());
        assertEquals(16, new KartAdulto(1, EstadoKart.DISPONIBLE, 0, 0, 0, "TR-101").edadMinimaPiloto());
    }

    @Test
    void unKartEnMantenimientoNoPuedeSalirAPista() {
        var kart = new KartAdulto(1, EstadoKart.DISPONIBLE, 0, 0, 0, "TR-101");
        kart.bloquear();
        assertThrows(ReglaNegocioException.class, kart::ponerEnPista);
    }

    @Test
    void elUsoSumaHorasYDetectaElUmbral() {
        var kart = new KartAdulto(1, EstadoKart.DISPONIBLE, 0, 0, 19.9, "TR-101");
        kart.ponerEnPista();
        kart.registrarUso(10, 5.1);
        assertEquals(EstadoKart.DISPONIBLE, kart.getEstado());
        assertTrue(kart.requiereMantenimiento(20));
    }

    @Test
    void elPrimerPasoAbreLaVueltaYLasLecturasCortasSeDescartan() {
        var a = new AsignacionKart(1, UUID.randomUUID(), "1", "Ana", 1, "TR-101", null, List.of());
        Instant t0 = Instant.parse("2026-11-20T23:00:00Z");
        assertTrue(a.registrarPaso(t0, Duration.ofSeconds(25)).isEmpty());
        assertTrue(a.registrarPaso(t0.plusSeconds(3), Duration.ofSeconds(25)).isEmpty());
        var v = a.registrarPaso(t0.plusMillis(31_500), Duration.ofSeconds(25));
        assertTrue(v.isPresent());
        assertEquals(31_500, v.get().milisegundos());
    }

    @Test
    void velocidadPromedioEnKmH() {
        var v = new VueltaTelemetria(1, Instant.now(), Duration.ofSeconds(34));
        assertEquals(90.0, v.velocidadPromedio(850), 0.01);
    }

    @Test
    void laFacturaDiscriminaElIvaDel19PorCiento() {
        Factura f = Factura.emitir("KRFE-000001", UUID.randomUUID(), new BigDecimal("119000"), "900123456-7",
                LocalDateTime.of(2026, 11, 20, 18, 0));
        assertEquals(0, new BigDecimal("100000").compareTo(f.subtotal()));
        assertEquals(0, new BigDecimal("19000").compareTo(f.iva()));
        assertEquals(96, f.cufe().length());
    }

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 20, 17, 0);

    private static Cliente cliente(int edad) {
        return new Cliente("1", "Ana", "ana@correo.co", "300", AHORA.toLocalDate().minusYears(edad).minusDays(5), true,
                AHORA.toLocalDate());
    }

    private static SesionPista tanda(TipoKart tipo, LocalDateTime inicio) {
        return SesionPista.programar("T-1", inicio, 10, 4, tipo);
    }

    @Test
    void lasEdadesPermitidasSalenDelTipoDeKart() {
        assertEquals(16, TipoKart.ADULTO.edadMinima());
        assertEquals(8, TipoKart.INFANTIL.edadMinima());
        assertEquals(15, TipoKart.INFANTIL.edadMaxima());
        assertEquals(15, new KartInfantil(9, EstadoKart.DISPONIBLE, 0, 0, 0, "TR-109").edadMaximaPiloto());
    }

    @Test
    void unAdultoNoPuedeReservarUnaTandaInfantil() {
        var tarifa = new Tarifa(TipoKart.INFANTIL, new BigDecimal("30000"), LocalDate.of(2026, 1, 1));
        var e = assertThrows(ReglaNegocioException.class, () -> Reserva.crear(cliente(30),
                tanda(TipoKart.INFANTIL, AHORA.plusHours(1)), 1, tarifa, AHORA, Duration.ofMinutes(10)));
        assertTrue(e.getMessage().contains("hasta 15"));
    }

    @Test
    void unMenorDe16NoPuedeReservarUnaTandaDeAdultos() {
        var tarifa = new Tarifa(TipoKart.ADULTO, new BigDecimal("45000"), LocalDate.of(2026, 1, 1));
        assertThrows(ReglaNegocioException.class, () -> Reserva.crear(cliente(12),
                tanda(TipoKart.ADULTO, AHORA.plusHours(1)), 1, tarifa, AHORA, Duration.ofMinutes(10)));
    }

    @Test
    void noSeReservaUnaTandaCuyaHoraYaPaso() {
        var tarifa = new Tarifa(TipoKart.ADULTO, new BigDecimal("45000"), LocalDate.of(2026, 1, 1));
        assertThrows(ConflictoException.class, () -> Reserva.crear(cliente(30),
                tanda(TipoKart.ADULTO, AHORA.minusMinutes(1)), 1, tarifa, AHORA, Duration.ofMinutes(10)));
    }

    @Test
    void cuposInsuficientesEsUnConflictoDeEstado() {
        var sesion = tanda(TipoKart.ADULTO, AHORA.plusHours(1));
        assertThrows(ConflictoException.class, () -> sesion.reservarCupos(5));
    }
}
