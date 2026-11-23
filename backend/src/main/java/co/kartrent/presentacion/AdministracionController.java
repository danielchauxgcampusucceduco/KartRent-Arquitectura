package co.kartrent.presentacion;

import co.kartrent.aplicacion.administracion.ConfigurarOperacion;
import co.kartrent.aplicacion.administracion.ReporteOcupacion;
import co.kartrent.dominio.reservas.Tarifa;
import co.kartrent.presentacion.Dtos.NuevaTarifa;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** HU-05 — Administración: tarifas y reporte de ocupación. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "HU-05 Administración")
public class AdministracionController {
    private final ConfigurarOperacion configurar;
    private final ReporteOcupacion reporte;
    private final Clock reloj;

    public AdministracionController(ConfigurarOperacion configurar, ReporteOcupacion reporte, Clock reloj) {
        this.configurar = configurar;
        this.reporte = reporte;
        this.reloj = reloj;
    }

    @GetMapping("/tarifas")
    public List<Tarifa> tarifas() {
        return configurar.tarifas();
    }

    @PostMapping("/tarifas")
    @ResponseStatus(HttpStatus.CREATED)
    public Tarifa registrar(@Valid @RequestBody NuevaTarifa t) {
        return configurar.registrarTarifa(t.tipoKart(), t.valorPorCupo(),
                t.vigenteDesde() == null ? LocalDate.now(reloj) : t.vigenteDesde());
    }

    @GetMapping("/reportes/ocupacion")
    @Operation(summary = "Ocupación por tanda e ingresos confirmados en un rango de fechas (RN-13)")
    public ReporteOcupacion.Reporte ocupacion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        LocalDate hoy = LocalDate.now(reloj);
        return reporte.ejecutar(desde == null ? hoy.minusDays(30) : desde, hasta == null ? hoy.plusDays(30) : hasta);
    }
}
