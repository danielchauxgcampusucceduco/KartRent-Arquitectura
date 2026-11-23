package co.kartrent.presentacion;

import co.kartrent.aplicacion.reservas.*;
import co.kartrent.presentacion.Dtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** HU-01 — Reservar y pagar una tanda. Controlador (GRASP): delega en los casos de uso. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "HU-01 Reservas y pagos")
public class ReservasController {
    private final RegistrarCliente registrarCliente;
    private final ReservarTanda reservarTanda;
    private final PagarReserva pagarReserva;
    private final CancelarReserva cancelarReserva;
    private final ConsultarReservas consultar;
    private final Clock reloj;

    public ReservasController(RegistrarCliente registrarCliente, ReservarTanda reservarTanda, PagarReserva pagarReserva,
                              CancelarReserva cancelarReserva, ConsultarReservas consultar, Clock reloj) {
        this.registrarCliente = registrarCliente;
        this.reservarTanda = reservarTanda;
        this.pagarReserva = pagarReserva;
        this.cancelarReserva = cancelarReserva;
        this.consultar = consultar;
        this.reloj = reloj;
    }

    @PostMapping("/clientes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar cliente con exoneración de responsabilidad (RN-03)")
    public ClienteDto registrar(@Valid @RequestBody NuevoCliente c) {
        var cliente = registrarCliente.ejecutar(new RegistrarCliente.Comando(c.documento(), c.nombre(), c.correo(),
                c.telefono(), c.fechaNacimiento(), c.aceptaExoneracion()));
        return ClienteDto.de(cliente, LocalDate.now(reloj));
    }

    @GetMapping("/clientes")
    public List<ClienteDto> clientes() {
        return consultar.clientes().stream().map(c -> ClienteDto.de(c, LocalDate.now(reloj))).toList();
    }

    @GetMapping("/clientes/{documento}")
    public ClienteDto cliente(@PathVariable String documento) {
        return ClienteDto.de(consultar.cliente(documento), LocalDate.now(reloj));
    }

    @GetMapping("/clientes/{documento}/reservas")
    public List<ReservaDto> reservasDe(@PathVariable String documento) {
        return consultar.deCliente(documento).stream().map(ReservaDto::de).toList();
    }

    @PostMapping("/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reservar cupos; quedan retenidos 10 minutos (RN-01)")
    public ReservaDto reservar(@Valid @RequestBody NuevaReserva r) {
        return ReservaDto.de(reservarTanda.ejecutar(r.documentoCliente(), r.codigoSesion(), r.cupos()));
    }

    @GetMapping("/reservas/{codigo}")
    public ReservaDto reserva(@PathVariable UUID codigo) {
        return ReservaDto.de(consultar.reserva(codigo));
    }

    @PatchMapping("/reservas/{codigo}")
    @Operation(summary = "Cancelar la reserva (estado CANCELADA)",
            description = "Pendiente de pago: libera los cupos. Confirmada: reembolsa el pago y pasa a REEMBOLSADA. "
                    + "409 si ya estaba cancelada, expirada o reembolsada, si su piloto ya tiene kart asignado o si la "
                    + "tanda ya comenzó; 400 si el estado pedido no es CANCELADA; 404 si la reserva no existe.")
    public ReservaDto cancelar(@PathVariable UUID codigo, @Valid @RequestBody CambioEstado cambio) {
        return ReservaDto.de(cancelarReserva.ejecutar(codigo));
    }

    @PostMapping("/reservas/{codigo}/pagos")
    @Operation(summary = "Pagar la reserva. Idempotente con la cabecera Idempotency-Key",
            description = "201 cuando se registra un intento de pago nuevo, aprobado o rechazado: si pago.aprobado es false "
                    + "la reserva sigue PENDIENTE_PAGO y se puede reintentar (con otra Idempotency-Key, o sin clave). 200 "
                    + "cuando la clave ya se había usado (no se cobra de nuevo). 409 si la reserva ya no está pendiente, la "
                    + "retención expiró o la clave pertenece a otra reserva.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
            description = "Intento de pago registrado (aprobado o rechazado)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Idempotency-Key repetida: se devuelve el resultado anterior sin cobrar de nuevo")
    public ResponseEntity<PagoDto> pagar(@PathVariable UUID codigo, @Valid @RequestBody NuevoPago p,
                                         @RequestHeader(name = "Idempotency-Key", required = false) String clave) {
        var r = pagarReserva.ejecutar(codigo, p.metodo(), clave);
        return ResponseEntity.status(r.repeticion() ? HttpStatus.OK : HttpStatus.CREATED).body(aDto(r));
    }

    @GetMapping("/reservas/{codigo}/factura")
    public FacturaDto factura(@PathVariable UUID codigo) {
        return FacturaDto.de(consultar.factura(codigo));
    }

    @PostMapping("/pagos/webhook")
    @Operation(summary = "Confirmación asíncrona de la pasarela, firmada con HMAC-SHA256 (X-Firma)",
            description = "Aprobación tardía: si la reserva ya expiró o se canceló, el cobro se reembolsa y el pago "
                    + "queda con reembolsado=true.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Notificación procesada")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "La firma X-Firma no coincide")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "La referencia de pago no existe")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "El cuerpo no es un JSON válido")
    public PagoDto webhook(@RequestBody String cuerpo, @RequestHeader(name = "X-Firma", required = false) String firma) {
        return aDto(pagarReserva.procesarWebhook(cuerpo, firma));
    }

    private static PagoDto aDto(PagarReserva.Resultado r) {
        return new PagoDto(ReservaDto.de(r.reserva()), PagoInfoDto.de(r.pago()), FacturaDto.de(r.factura()));
    }
}
