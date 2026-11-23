package co.kartrent.presentacion;

import co.kartrent.aplicacion.reservas.*;
import co.kartrent.infraestructura.pagos.VerificadorFirmaWebhook;
import co.kartrent.presentacion.Dtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ConsultarReservas consultar;
    private final VerificadorFirmaWebhook firma;
    private final ObjectMapper json;
    private final Clock reloj;

    public ReservasController(RegistrarCliente registrarCliente, ReservarTanda reservarTanda, PagarReserva pagarReserva,
                              ConsultarReservas consultar, VerificadorFirmaWebhook firma, ObjectMapper json, Clock reloj) {
        this.registrarCliente = registrarCliente;
        this.reservarTanda = reservarTanda;
        this.pagarReserva = pagarReserva;
        this.consultar = consultar;
        this.firma = firma;
        this.json = json;
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

    @PostMapping("/reservas/{codigo}/pagos")
    @Operation(summary = "Pagar la reserva. Idempotente con la cabecera Idempotency-Key")
    public PagoDto pagar(@PathVariable UUID codigo, @Valid @RequestBody NuevoPago p,
                         @RequestHeader(name = "Idempotency-Key", required = false) String clave) {
        var r = pagarReserva.ejecutar(codigo, p.metodo(), clave == null ? codigo.toString() : clave);
        return new PagoDto(ReservaDto.de(r.reserva()), r.pago(), r.factura());
    }

    @GetMapping("/reservas/{codigo}/factura")
    public ResponseEntity<?> factura(@PathVariable UUID codigo) {
        return consultar.factura(codigo).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/pagos/webhook")
    @Operation(summary = "Confirmación asíncrona de la pasarela, firmada con HMAC-SHA256 (X-Firma)")
    public ResponseEntity<?> webhook(@RequestBody String cuerpo, @RequestHeader(name = "X-Firma", required = false) String f)
            throws Exception {
        if (!firma.esValida(cuerpo, f)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        WebhookPago w = json.readValue(cuerpo, WebhookPago.class);
        var r = pagarReserva.confirmarDesdePasarela(w.referencia(), w.aprobado());
        return ResponseEntity.ok(new PagoDto(ReservaDto.de(r.reserva()), r.pago(), r.factura()));
    }
}
