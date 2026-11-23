package co.kartrent.dominio.reservas;

import co.kartrent.dominio.comun.ReglaNegocioException;
import co.kartrent.dominio.facturacion.Pago;
import co.kartrent.dominio.flota.KartAdulto;
import co.kartrent.dominio.flota.KartInfantil;
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
        int edadMinima = KartInfantil.TIPO.equals(sesion.getTipoKart()) ? 8 : 16;
        if (!KartAdulto.TIPO.equals(sesion.getTipoKart()) && !KartInfantil.TIPO.equals(sesion.getTipoKart())) {
            throw new ReglaNegocioException("Tipo de kart desconocido");
        }
        if (cliente.calcularEdad(ahora.toLocalDate()) < edadMinima) {
            throw new ReglaNegocioException("El piloto no cumple la edad mínima para karts " + sesion.getTipoKart());
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

    public Pago registrarPago(String referencia, String metodo, boolean aprobado, LocalDateTime ahora) {
        if (estado != EstadoReserva.PENDIENTE_PAGO) {
            throw new ReglaNegocioException("La reserva no está pendiente de pago");
        }
        if (ahora.isAfter(venceRetencion)) {
            throw new ReglaNegocioException("La retención de cupos expiró; vuelve a reservar");
        }
        return new Pago(referencia, codigo, valorTotal, metodo, aprobado, ahora);
    }

    public void confirmar(Pago pago) {
        if (!pago.esAprobado()) throw new ReglaNegocioException("El pago no fue aprobado");
        if (estado != EstadoReserva.PENDIENTE_PAGO) throw new ReglaNegocioException("La reserva no está pendiente de pago");
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

    public void cancelar() {
        if (estado == EstadoReserva.CONFIRMADA) throw new ReglaNegocioException("Una reserva pagada se gestiona por reembolso");
        estado = EstadoReserva.CANCELADA;
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
