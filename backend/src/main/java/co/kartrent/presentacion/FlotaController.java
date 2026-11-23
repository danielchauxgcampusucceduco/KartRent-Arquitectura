package co.kartrent.presentacion;

import co.kartrent.aplicacion.administracion.ConfigurarOperacion;
import co.kartrent.aplicacion.flota.ConsultarFlota;
import co.kartrent.aplicacion.flota.GestionarOrdenes;
import co.kartrent.aplicacion.puertos.ParametrosOperacion;
import co.kartrent.presentacion.Dtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** HU-04 — Mantenimiento preventivo de la flota. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "HU-04 Flota y taller")
public class FlotaController {
    private final ConsultarFlota flota;
    private final GestionarOrdenes ordenes;
    private final ConfigurarOperacion configurar;
    private final ParametrosOperacion parametros;

    public FlotaController(ConsultarFlota flota, GestionarOrdenes ordenes, ConfigurarOperacion configurar,
                           ParametrosOperacion parametros) {
        this.flota = flota;
        this.ordenes = ordenes;
        this.configurar = configurar;
        this.parametros = parametros;
    }

    @GetMapping("/flota/karts")
    public List<KartDto> karts() {
        double umbral = parametros.umbralHorasPreventivo();
        return flota.todos().stream().map(k -> KartDto.de(k, umbral)).toList();
    }

    @GetMapping("/flota/umbral")
    public Map<String, Double> umbral() {
        return Map.of("horas", configurar.umbralActual());
    }

    @PutMapping("/flota/umbral")
    @Operation(summary = "Cambiar el umbral de horas de motor para el preventivo (RN-09)")
    public Map<String, Double> actualizarUmbral(@Valid @RequestBody NuevoUmbral u) {
        return Map.of("horas", configurar.actualizarUmbral(u.horas()));
    }

    @GetMapping("/ordenes")
    public List<OrdenDto> ordenes() {
        return ordenes.listar().stream().map(OrdenDto::de).toList();
    }

    @GetMapping("/mecanicos")
    public List<MecanicoDto> mecanicos() {
        return ordenes.mecanicos().stream().map(MecanicoDto::de).toList();
    }

    @PostMapping("/ordenes/{numero}/asignacion")
    public OrdenDto asignar(@PathVariable String numero, @Valid @RequestBody AsignarMecanico a) {
        return OrdenDto.de(ordenes.asignar(numero, a.codigoMecanico()));
    }

    @PostMapping("/ordenes/{numero}/cierre")
    @Operation(summary = "Cerrar la orden; solo el mecánico asignado (RN-10). El kart vuelve a DISPONIBLE")
    public OrdenDto cerrar(@PathVariable String numero, @Valid @RequestBody CerrarOrden c) {
        return OrdenDto.de(ordenes.cerrar(numero, c.codigoMecanico(), c.observaciones()));
    }
}
