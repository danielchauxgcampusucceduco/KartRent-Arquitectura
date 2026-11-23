package co.kartrent.dominio.reservas;

import co.kartrent.dominio.comun.ConflictoException;
import co.kartrent.dominio.comun.ReglaNegocioException;
import co.kartrent.dominio.facturacion.MetodoPago;
import co.kartrent.dominio.facturacion.Pago;
import co.kartrent.dominio.flota.TipoKart;
import co.kartrent.dominio.pista.SesionPista;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/** Experto en su ciclo de vida y Creador de sus pagos (GRASP). */
public class Reserva {
    private final UUID codigo;
    private final String documentoCliente;
    private final String codigoSesion;
    private final LocalDateTime fechaCreacion;
    private final int cantidadCupos;
    private final BigDecimal valorTotal;
    private EstadoReserva estado;
    private final LocalDateTime venceRetencion;

    public Reserva(UUID codigo, String documentoCliente, String codigoSesion, LocalDateTime fechaCreacion,
                   int cantidadCupos, BigDecimal valorTotal, EstadoReserva estado, LocalDateTime venceRetencion) {
        this.codigo = codigo;
        this.documentoCliente = documentoCliente;
        this.codigoSesion = codigoSesion;
        this.fechaCreacion = fechaCreacion;
        this.cantidadCupos = cantidadCupos;
        this.valorTotal = valorTotal;
        this.estado = estado;
        this.venceRetencion = venceRetencion;
    }

    /**
     * Crea la reserva reteniendo los cupos.
     * RN-03 exoneración firmada, RN-02 edad mínima del tipo de kart, RN-01 retención temporal.
     */
    public static Reserva crear(Cliente cliente, SesionPista sesion, int cupos, Tarifa tarifa,
                                LocalDateTime ahora, Duration retencion) {
        if (!cliente.isExoneracionFirmada()) {
            throw new ReglaNegocioException("El cliente debe firmar la exoneración de responsabilidad");
        }
        TipoKart tipo = sesion.getTipoKart();
        int edad = cliente.calcularEdad(ahora.toLocalDate());
        if (edad < tipo.edadMinima()) {
            throw new ReglaNegocioException("El piloto no cumple la edad mínima para karts " + tipo);
        }
        if (edad > tipo.edadMaxima()) {
            throw new ReglaNegocioException("Los karts " + tipo + " son solo para pilotos de hasta "
                    + tipo.edadMaxima() + " años");
        }
        if (!sesion.getHoraInicioProgramada().isAfter(ahora)) {
            throw new ConflictoException("La tanda " + sesion.getCodigo() + " ya comenzó; elige una tanda futura");
        }
        if (!tarifa.tipoKart().equals(sesion.getTipoKart())) {
            throw new ReglaNegocioException("La tarifa no corresponde al tipo de kart de la tanda");
        }
        sesion.reservarCupos(cupos);
        return new Reserva(UUID.randomUUID(), cliente.getDocumento(), sesion.getCodigo(), ahora, cupos,
                calcularTotal(tarifa.valorPorCupo(), cupos), EstadoReserva.PENDIENTE_PAGO, ahora.plus(retencion));
    }

    public static BigDecimal calcularTotal(BigDecimal tarifaPorCupo, int cupos) {
        return tarifaPorCupo.multiply(BigDecimal.valueOf(cupos));
    }

    public Pago registrarPago(String referencia, MetodoPago metodo, boolean aprobado, LocalDateTime ahora) {
        verificarPagable(ahora);
        return new Pago(referencia, codigo, valorTotal, metodo, aprobado, ahora);
    }

    /** Se invoca antes de cobrar en la pasarela: nunca se cobra una reserva que ya no se puede pagar. */
    public void verificarPagable(LocalDateTime ahora) {
        if (estado != EstadoReserva.PENDIENTE_PAGO) {
            throw new ConflictoException("La reserva no está pendiente de pago");
        }
        if (ahora.isAfter(venceRetencion)) {
            throw new ConflictoException("La retención de cupos expiró; vuelve a reservar");
        }
    }

    public void confirmar(Pago pago) {
        if (!pago.esAprobado()) throw new ReglaNegocioException("El pago no fue aprobado");
        if (estado != EstadoReserva.PENDIENTE_PAGO) throw new ConflictoException("La reserva no está pendiente de pago");
        estado = EstadoReserva.CONFIRMADA;
    }

    /** RN-01: devuelve true si la reserva expiró en este llamado. */
    public boolean expirar(LocalDateTime ahora) {
        if (estado == EstadoReserva.PENDIENTE_PAGO && ahora.isAfter(venceRetencion)) {
            estado = EstadoReserva.EXPIRADA;
            return true;
        }
        return false;
    }

    /** El cliente (o el administrador) abandona una reserva que aún no se pagó: los cupos se liberan. */
    public void cancelar() {
        if (estado != EstadoReserva.PENDIENTE_PAGO) {
            throw new ConflictoException("Solo se cancela una reserva pendiente de pago; estado actual: " + estado);
        }
        estado = EstadoReserva.CANCELADA;
    }

    /** Una reserva pagada se cancela devolviendo el dinero. */
    public void reembolsar() {
        if (estado != EstadoReserva.CONFIRMADA) {
            throw new ConflictoException("Solo se reembolsa una reserva confirmada; estado actual: " + estado);
        }
        estado = EstadoReserva.REEMBOLSADA;
    }

    public UUID getCodigo() { return codigo; }
    public String getDocumentoCliente() { return documentoCliente; }
    public String getCodigoSesion() { return codigoSesion; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public int getCantidadCupos() { return cantidadCupos; }
    public BigDecimal getValorTotal() { return valorTotal; }
    public EstadoReserva getEstado() { return estado; }
    public LocalDateTime getVenceRetencion() { return venceRetencion; }
}
