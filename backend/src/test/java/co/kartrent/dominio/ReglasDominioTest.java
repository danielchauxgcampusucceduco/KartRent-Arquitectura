package co.kartrent.dominio;

import co.kartrent.dominio.comun.ReglaNegocioException;
import co.kartrent.dominio.facturacion.Factura;
import co.kartrent.dominio.flota.EstadoKart;
import co.kartrent.dominio.flota.KartAdulto;
import co.kartrent.dominio.flota.KartInfantil;
import co.kartrent.dominio.pista.AsignacionKart;
import co.kartrent.dominio.pista.VueltaTelemetria;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
}
