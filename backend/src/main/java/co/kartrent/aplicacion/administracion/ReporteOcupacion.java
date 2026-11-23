package co.kartrent.aplicacion.administracion;

import co.kartrent.aplicacion.puertos.RepositorioReservas;
import co.kartrent.aplicacion.puertos.RepositorioSesiones;
import co.kartrent.dominio.pista.SesionPista;
import co.kartrent.dominio.reservas.Reserva;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** HU-05 — RN-13: ocupación = cupos vendidos / cupos ofrecidos. */
public class ReporteOcupacion {
    private final RepositorioSesiones sesiones;
    private final RepositorioReservas reservas;

    public ReporteOcupacion(RepositorioSesiones sesiones, RepositorioReservas reservas) {
        this.sesiones = sesiones;
        this.reservas = reservas;
    }

    public Reporte ejecutar(LocalDate desde, LocalDate hasta) {
        List<Linea> lineas = sesiones.listar().stream()
                .filter(s -> !s.getHoraInicioProgramada().toLocalDate().isBefore(desde)
                        && !s.getHoraInicioProgramada().toLocalDate().isAfter(hasta))
                .sorted(Comparator.comparing(SesionPista::getHoraInicioProgramada))
                .map(this::linea)
                .toList();
        int ofrecidos = lineas.stream().mapToInt(Linea::cuposOfrecidos).sum();
        int vendidos = lineas.stream().mapToInt(Linea::cuposVendidos).sum();
        BigDecimal ingresos = lineas.stream().map(Linea::ingresos).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Reporte(lineas, ofrecidos, vendidos, porcentaje(vendidos, ofrecidos), ingresos);
    }

    private Linea linea(SesionPista s) {
        BigDecimal ingresos = reservas.confirmadasPorSesion(s.getCodigo()).stream()
                .map(Reserva::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Linea(s.getCodigo(), s.getHoraInicioProgramada().toString(), s.getTipoKart().name(), s.getCuposMaximos(),
                s.getCuposVendidos(), porcentaje(s.getCuposVendidos(), s.getCuposMaximos()), ingresos);
    }

    static double porcentaje(int parte, int total) {
        if (total == 0) return 0;
        return BigDecimal.valueOf(parte * 100.0 / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public record Linea(String codigoSesion, String inicio, String tipoKart, int cuposOfrecidos, int cuposVendidos,
                        double ocupacionPorcentaje, BigDecimal ingresos) {
    }

    public record Reporte(List<Linea> tandas, int cuposOfrecidos, int cuposVendidos, double ocupacionPorcentaje,
                          BigDecimal ingresos) {
    }
}
