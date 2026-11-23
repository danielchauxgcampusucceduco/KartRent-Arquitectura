package co.kartrent.presentacion;

import co.kartrent.aplicacion.administracion.ReporteOcupacion;
import co.kartrent.aplicacion.pista.ControlarSesion;
import co.kartrent.dominio.eventos.VueltaRegistrada;
import co.kartrent.dominio.facturacion.Factura;
import co.kartrent.dominio.facturacion.MetodoPago;
import co.kartrent.dominio.facturacion.Pago;
import co.kartrent.dominio.flota.Kart;
import co.kartrent.dominio.flota.Mecanico;
import co.kartrent.dominio.flota.OrdenMantenimiento;
import co.kartrent.dominio.flota.TipoKart;
import co.kartrent.dominio.pista.AsignacionKart;
import co.kartrent.dominio.pista.LineaClasificacion;
import co.kartrent.dominio.pista.SesionPista;
import co.kartrent.dominio.pista.UsoKart;
import co.kartrent.dominio.reservas.Cliente;
import co.kartrent.dominio.reservas.Reserva;
import co.kartrent.dominio.reservas.Tarifa;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de entrada y salida de la API REST. Ningún controlador devuelve una entidad del dominio ni un
 * registro de la capa de aplicación: todo se copia a un DTO de este archivo.
 */
public final class Dtos {
    private Dtos() {
    }

    // ---------- Entradas ----------
    public record NuevoCliente(@NotBlank @Size(max = 20) String documento, @NotBlank String nombre,
                               @NotBlank @Email String correo, String telefono, @NotNull @Past LocalDate fechaNacimiento,
                               boolean aceptaExoneracion) {
    }

    public record NuevaReserva(@NotBlank String documentoCliente, @NotBlank String codigoSesion, @Min(1) @Max(20) int cupos) {
    }

    /** Cuerpo de PATCH: el único cambio de estado que el cliente puede pedir es CANCELADA. */
    public record CambioEstado(@NotBlank @Pattern(regexp = "CANCELADA", message = "Solo se admite el estado CANCELADA")
                               String estado) {
    }

    public record NuevoPago(@NotNull MetodoPago metodo) {
    }

    public record NuevaAsignacion(@NotNull UUID codigoReserva, @NotBlank String documentoPiloto, @Min(1) int numeroKart) {
    }

    public record PasoTransponder(@NotBlank String codigoTransponder, Instant marcaTiempo) {
    }

    public record AsignarMecanico(@NotBlank String codigoMecanico) {
    }

    public record CerrarOrden(@NotBlank String codigoMecanico, @NotBlank @Size(max = 500) String observaciones) {
    }

    public record NuevaTarifa(@NotNull TipoKart tipoKart, @NotNull @Positive BigDecimal valorPorCupo, LocalDate vigenteDesde) {
    }

    public record NuevaTanda(@NotBlank @Size(max = 20) String codigo, @NotNull LocalDateTime inicio, @Min(5) int duracionMinutos,
                             @Min(1) @Max(20) int cupos, @NotNull TipoKart tipoKart) {
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

    public record PagoInfoDto(String referenciaPasarela, BigDecimal monto, String metodo, boolean aprobado,
                              boolean reembolsado, LocalDateTime fechaPago) {
        static PagoInfoDto de(Pago p) {
            return new PagoInfoDto(p.referenciaPasarela(), p.monto(), p.metodo().name(), p.aprobado(), p.reembolsado(),
                    p.fechaPago());
        }
    }

    public record FacturaDto(String numero, String cufe, UUID codigoReserva, LocalDateTime fechaEmision,
                             BigDecimal subtotal, BigDecimal iva, BigDecimal total) {
        static FacturaDto de(Factura f) {
            return f == null ? null : new FacturaDto(f.numero(), f.cufe(), f.codigoReserva(), f.fechaEmision(),
                    f.subtotal(), f.iva(), f.total());
        }
    }

    public record PagoDto(ReservaDto reserva, PagoInfoDto pago, FacturaDto factura) {
    }

    public record TarifaDto(String tipoKart, BigDecimal valorPorCupo, LocalDate vigenteDesde) {
        static TarifaDto de(Tarifa t) {
            return new TarifaDto(t.tipoKart().name(), t.valorPorCupo(), t.vigenteDesde());
        }
    }

    public record SesionDto(String codigo, LocalDateTime horaInicio, int duracionMinutos, int cuposMaximos,
                            int cuposVendidos, int cuposDisponibles, String tipoKart, String estado,
                            LocalDateTime inicioReal, LocalDateTime finReal, List<AsignacionDto> asignaciones) {
        static SesionDto de(SesionPista s) {
            return new SesionDto(s.getCodigo(), s.getHoraInicioProgramada(), s.getDuracionMinutos(), s.getCuposMaximos(),
                    s.getCuposVendidos(), s.cuposDisponibles(), s.getTipoKart().name(), s.getEstado().name(),
                    s.getInicioReal(), s.getFinReal(), s.getAsignaciones().stream().map(AsignacionDto::de).toList());
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
            return new KartDto(k.getNumeroKart(), k.tipo().name(), k.getEstado().name(), k.getKilometraje(),
                    k.getHorasMotor(), k.getHorasDesdeUltimoServicio(), k.getCodigoTransponder(),
                    k.requiereMantenimiento(umbral));
        }
    }

    public record ReservaPendienteDto(UUID codigoReserva, String documentoCliente, String nombreCliente, int cupos,
                                      int asignados) {
        static ReservaPendienteDto de(ControlarSesion.ReservaPendiente r) {
            return new ReservaPendienteDto(r.codigoReserva(), r.documentoCliente(), r.nombreCliente(), r.cupos(),
                    r.asignados());
        }
    }

    public record ParrillaDto(SesionDto sesion, List<ReservaPendienteDto> reservas, List<KartDto> kartsDisponibles) {
        static ParrillaDto de(ControlarSesion.Parrilla p) {
            return new ParrillaDto(SesionDto.de(p.sesion()),
                    p.reservas().stream().map(ReservaPendienteDto::de).toList(),
                    p.kartsDisponibles().stream().map(k -> KartDto.de(k, p.umbralHoras())).toList());
        }
    }

    public record LineaClasificacionDto(int posicion, int numeroKart, String piloto, int vueltas, Long mejorVueltaMs,
                                        Long ultimaVueltaMs) {
        static LineaClasificacionDto de(LineaClasificacion l) {
            return new LineaClasificacionDto(l.posicion(), l.numeroKart(), l.piloto(), l.vueltas(), l.mejorVueltaMs(),
                    l.ultimaVueltaMs());
        }
    }

    public record ClasificacionDto(String codigoSesion, String estado, List<LineaClasificacionDto> clasificacion) {
        static ClasificacionDto de(SesionPista s) {
            return new ClasificacionDto(s.getCodigo(), s.getEstado().name(),
                    s.obtenerClasificacion().stream().map(LineaClasificacionDto::de).toList());
        }
    }

    public record UsoKartDto(int numeroKart, int minutos, double kilometros) {
        static UsoKartDto de(UsoKart u) {
            return new UsoKartDto(u.numeroKart(), u.minutos(), u.kilometros());
        }
    }

    public record CierreDto(SesionDto sesion, List<UsoKartDto> usos, List<OrdenDto> ordenesGeneradas) {
        static CierreDto de(ControlarSesion.Cierre c) {
            return new CierreDto(SesionDto.de(c.sesion()), c.usos().stream().map(UsoKartDto::de).toList(),
                    c.ordenesGeneradas().stream().map(OrdenDto::de).toList());
        }
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

    public record VueltaDto(String codigoSesion, int numeroKart, int numeroVuelta, long tiempoMs, LocalDateTime ocurrido) {
        static VueltaDto de(VueltaRegistrada v) {
            return v == null ? null : new VueltaDto(v.codigoSesion(), v.numeroKart(), v.numeroVuelta(), v.tiempoMs(),
                    v.ocurrido());
        }
    }

    public record PasoDto(boolean vueltaRegistrada, VueltaDto vuelta) {
    }

    public record LineaReporteDto(String codigoSesion, String inicio, String tipoKart, int cuposOfrecidos,
                                  int cuposVendidos, double ocupacionPorcentaje, BigDecimal ingresos) {
        static LineaReporteDto de(ReporteOcupacion.Linea l) {
            return new LineaReporteDto(l.codigoSesion(), l.inicio(), l.tipoKart(), l.cuposOfrecidos(), l.cuposVendidos(),
                    l.ocupacionPorcentaje(), l.ingresos());
        }
    }

    public record ReporteDto(List<LineaReporteDto> tandas, int cuposOfrecidos, int cuposVendidos,
                             double ocupacionPorcentaje, BigDecimal ingresos) {
        static ReporteDto de(ReporteOcupacion.Reporte r) {
            return new ReporteDto(r.tandas().stream().map(LineaReporteDto::de).toList(), r.cuposOfrecidos(),
                    r.cuposVendidos(), r.ocupacionPorcentaje(), r.ingresos());
        }
    }
}
