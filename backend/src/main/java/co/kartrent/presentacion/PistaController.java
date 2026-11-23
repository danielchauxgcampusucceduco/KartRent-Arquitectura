package co.kartrent.presentacion;

import co.kartrent.aplicacion.pista.ControlarSesion;
import co.kartrent.aplicacion.pista.RegistrarPaso;
import co.kartrent.presentacion.Dtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** HU-02 (boxes y parrilla) y HU-03 (cronometraje en vivo). */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "HU-02/HU-03 Pista")
public class PistaController {
    private final ControlarSesion controlar;
    private final RegistrarPaso registrarPaso;
    private final Clock reloj;

    public PistaController(ControlarSesion controlar, RegistrarPaso registrarPaso, Clock reloj) {
        this.controlar = controlar;
        this.registrarPaso = registrarPaso;
        this.reloj = reloj;
    }

    @GetMapping("/sesiones")
    public List<SesionDto> sesiones() {
        return controlar.listar().stream().map(SesionDto::de).toList();
    }

    @GetMapping("/sesiones/{codigo}")
    public SesionDto sesion(@PathVariable String codigo) {
        return SesionDto.de(controlar.sesion(codigo));
    }

    @GetMapping("/sesiones/{codigo}/parrilla")
    @Operation(summary = "Reservas confirmadas por asignar y karts disponibles del tipo de la tanda (RN-04)")
    public ParrillaDto parrilla(@PathVariable String codigo) {
        return ParrillaDto.de(controlar.parrilla(codigo));
    }

    @PostMapping("/sesiones/{codigo}/asignaciones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Asignar kart a piloto (RN-04 disponibilidad, RN-05 edad/tipo)")
    public AsignacionDto asignar(@PathVariable String codigo, @Valid @RequestBody NuevaAsignacion a) {
        return AsignacionDto.de(controlar.asignarKart(codigo, a.codigoReserva(), a.documentoPiloto(), a.numeroKart()));
    }

    @PostMapping("/sesiones/{codigo}/iniciar")
    public SesionDto iniciar(@PathVariable String codigo) {
        return SesionDto.de(controlar.iniciar(codigo));
    }

    @PostMapping("/sesiones/{codigo}/finalizar")
    @Operation(summary = "Finalizar tanda: registra uso y abre órdenes preventivas (HU-04)")
    public CierreDto finalizar(@PathVariable String codigo) {
        return CierreDto.de(controlar.finalizar(codigo));
    }

    @GetMapping("/sesiones/{codigo}/clasificacion")
    @Operation(summary = "Clasificación ordenada por mejor vuelta (RN-08). En vivo: STOMP /topic/sesiones/{codigo}/clasificacion")
    public ClasificacionDto clasificacion(@PathVariable String codigo) {
        return ClasificacionDto.de(controlar.sesion(codigo));
    }

    @PostMapping("/telemetria/pasos")
    @Operation(summary = "Paso de un transponder por la línea de meta. Vueltas < 25 s se descartan (RN-07)")
    public PasoDto paso(@Valid @RequestBody PasoTransponder p) {
        // Precisión de milisegundos: es la que conserva la base de datos y la del decodificador.
        Instant marca = (p.marcaTiempo() == null ? reloj.instant() : p.marcaTiempo()).truncatedTo(ChronoUnit.MILLIS);
        var v = registrarPaso.ejecutar(p.codigoTransponder(), marca);
        return new PasoDto(v.isPresent(), VueltaDto.de(v.orElse(null)));
    }
}
