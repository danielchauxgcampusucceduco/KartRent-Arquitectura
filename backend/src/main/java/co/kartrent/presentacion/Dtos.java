package co.kartrent.presentacion;

import co.kartrent.aplicacion.pista.ControlarSesion;
import co.kartrent.dominio.facturacion.Factura;
import co.kartrent.dominio.facturacion.Pago;
import co.kartrent.dominio.flota.Kart;
import co.kartrent.dominio.flota.Mecanico;
import co.kartrent.dominio.flota.OrdenMantenimiento;
import co.kartrent.dominio.pista.AsignacionKart;
import co.kartrent.dominio.pista.LineaClasificacion;
import co.kartrent.dominio.pista.SesionPista;
import co.kartrent.dominio.reservas.Cliente;
import co.kartrent.dominio.reservas.Reserva;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs de entrada/salida: la API nunca expone las entidades del dominio directamente. */
public final class Dtos {
    private Dtos() {
    }

    // ---------- Entradas ----------
    public record NuevoCliente(@NotBlank @Size(max = 20) String documento, @NotBlank String nombre, @Email String correo,
                               String telefono, @NotNull @Past LocalDate fechaNacimiento, boolean aceptaExoneracion) {
    }

    public record NuevaReserva(@NotBlank String documentoCliente, @NotBlank String codigoSesion, @Min(1) @Max(20) int cupos) {
    }

    public record NuevoPago(@NotBlank String metodo) {
    }

    public record WebhookPago(@NotBlank String referencia, boolean aprobado) {
    }

    public record NuevaAsignacion(@NotNull UUID codigoReserva, @NotBlank String documentoPiloto, @Min(1) int numeroKart) {
    }

    public record PasoTransponder(@NotBlank String codigoTransponder, Instant marcaTiempo) {
    }

    public record AsignarMecanico(@NotBlank String codigoMecanico) {
    }

    public record CerrarOrden(@NotBlank String codigoMecanico, @NotBlank @Size(max = 500) String observaciones) {
    }

    public record NuevaTarifa(@NotBlank String tipoKart, @NotNull @Positive BigDecimal valorPorCupo, LocalDate vigenteDesde) {
    }

    public record NuevaTanda(@NotBlank String codigo, @NotNull LocalDateTime inicio, @Min(5) int duracionMinutos,
                             @Min(1) @Max(20) int cupos, @NotBlank String tipoKart) {
    }

    public record NuevoUmbral(@Positive double horas) {
    }

    // ---------- Salidas ----------
    public record ClienteDto(String documento, String nombre, String correo, String telefono, LocalDate fechaNacimiento,
                             int edad, boolean exoneracionFirmada) {
        static ClienteDto de(Cliente c, LocalDate hoy) {
            return new ClienteDto(c.getDocumento(), c.getNombre(), c.getCorreo(), c.getTelefono(), c.getFechaNacimiento(),
                    c.calcularEdad(hoy), c.isExoneracionFirmada());
        }
    }

    public record ReservaDto(UUID codigo, String documentoCliente, String codigoSesion, LocalDateTime fechaCreacion,
                             int cupos, BigDecimal valorTotal, String estado, LocalDateTime venceRetencion) {
        static ReservaDto de(Reserva r) {
            return new ReservaDto(r.getCodigo(), r.getDocumentoCliente(), r.getCodigoSesion(), r.getFechaCreacion(),
                    r.getCantidadCupos(), r.getValorTotal(), r.getEstado().name(), r.getVenceRetencion());
        }
    }

    public record PagoDto(ReservaDto reserva, Pago pago, Factura factura) {
    }

    public record SesionDto(String codigo, LocalDateTime horaInicio, int duracionMinutos, int cuposMaximos,
                            int cuposVendidos, int cuposDisponibles, String tipoKart, String estado,
                            LocalDateTime inicioReal, LocalDateTime finReal, List<AsignacionDto> asignaciones) {
        static SesionDto de(SesionPista s) {
            return new SesionDto(s.getCodigo(), s.getHoraInicioProgramada(), s.getDuracionMinutos(), s.getCuposMaximos(),
                    s.getCuposVendidos(), s.cuposDisponibles(), s.getTipoKart(), s.getEstado().name(), s.getInicioReal(),
                    s.getFinReal(), s.getAsignaciones().stream().map(AsignacionDto::de).toList());
        }
    }

    public record AsignacionDto(int numeroParrilla, UUID codigoReserva, String documentoPiloto, String nombrePiloto,
                                int numeroKart, String codigoTransponder, int vueltas) {
        static AsignacionDto de(AsignacionKart a) {
            return new AsignacionDto(a.getNumeroParrilla(), a.getCodigoReserva(), a.getDocumentoPiloto(),
                    a.getNombrePiloto(), a.getNumeroKart(), a.getCodigoTransponder(), a.totalVueltas());
        }
    }

    public record KartDto(int numero, String tipo, String estado, double kilometraje, double horasMotor,
                          double horasDesdeServicio, String codigoTransponder, boolean requiereMantenimiento) {
        static KartDto de(Kart k, double umbral) {
            return new KartDto(k.getNumeroKart(), k.tipo(), k.getEstado().name(), k.getKilometraje(), k.getHorasMotor(),
                    k.getHorasDesdeUltimoServicio(), k.getCodigoTransponder(), k.requiereMantenimiento(umbral));
        }
    }

    public record ParrillaDto(SesionDto sesion, List<ControlarSesion.ReservaPendiente> reservas, List<KartDto> kartsDisponibles) {
    }

    public record ClasificacionDto(String codigoSesion, String estado, List<LineaClasificacion> clasificacion) {
    }

    public record CierreDto(SesionDto sesion, List<co.kartrent.dominio.pista.UsoKart> usos, List<OrdenDto> ordenesGeneradas) {
    }

    public record OrdenDto(String numero, int numeroKart, String tipo, String estado, LocalDateTime fechaApertura,
                           LocalDateTime fechaCierre, double horasMotorAlAbrir, String codigoMecanico, String observaciones) {
        static OrdenDto de(OrdenMantenimiento o) {
            return new OrdenDto(o.getNumero(), o.getNumeroKart(), o.getTipo().name(), o.getEstado().name(),
                    o.getFechaApertura(), o.getFechaCierre(), o.getHorasMotorAlAbrir(), o.getCodigoMecanico(),
                    o.getObservaciones());
        }
    }

    public record MecanicoDto(String codigoEmpleado, String nombre, String especialidad) {
        static MecanicoDto de(Mecanico m) {
            return new MecanicoDto(m.getCodigoEmpleado(), m.getNombre(), m.getEspecialidad());
        }
    }

    public record PasoDto(boolean vueltaRegistrada, Object vuelta) {
    }
}
