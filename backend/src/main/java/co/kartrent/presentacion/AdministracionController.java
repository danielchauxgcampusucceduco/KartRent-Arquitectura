package co.kartrent.presentacion;

import co.kartrent.aplicacion.administracion.ConfigurarOperacion;
import co.kartrent.aplicacion.administracion.ReporteOcupacion;
import co.kartrent.presentacion.Dtos.*;
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
    public List<TarifaDto> tarifas() {
        return configurar.tarifas().stream().map(TarifaDto::de).toList();
    }

    @PostMapping("/tarifas")
    @ResponseStatus(HttpStatus.CREATED)
    public TarifaDto registrar(@Valid @RequestBody NuevaTarifa t) {
        return TarifaDto.de(configurar.registrarTarifa(t.tipoKart(), t.valorPorCupo(),
                t.vigenteDesde() == null ? LocalDate.now(reloj) : t.vigenteDesde()));
    }

    @PostMapping("/sesiones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Programar una tanda (HU-05)",
            description = "409 si el código ya existe o no hay tarifa vigente para el tipo de kart; 400 si el inicio no es futuro.")
    public SesionDto programar(@Valid @RequestBody NuevaTanda t) {
        return SesionDto.de(configurar.programarTanda(t.codigo(), t.inicio(), t.duracionMinutos(), t.cupos(), t.tipoKart()));
    }

    @GetMapping("/reportes/ocupacion")
    @Operation(summary = "Ocupación por tanda e ingresos confirmados en un rango de fechas (RN-13)")
    public ReporteDto ocupacion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        LocalDate hoy = LocalDate.now(reloj);
        return ReporteDto.de(reporte.ejecutar(desde == null ? hoy.minusDays(30) : desde, hasta == null ? hoy.plusDays(30) : hasta));
    }
}
