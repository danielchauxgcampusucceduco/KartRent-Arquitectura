package co.kartrent.dominio.flota;

import co.kartrent.dominio.comun.ConflictoException;
import co.kartrent.dominio.comun.ReglaNegocioException;

import java.time.LocalDateTime;

public class OrdenMantenimiento {
    private final String numero;
    private final int numeroKart;
    private final TipoMantenimiento tipo;
    private EstadoOrden estado;
    private final LocalDateTime fechaApertura;
    private LocalDateTime fechaCierre;
    private final double horasMotorAlAbrir;
    private String codigoMecanico;
    private String observaciones;

    public OrdenMantenimiento(String numero, int numeroKart, TipoMantenimiento tipo, EstadoOrden estado,
                              LocalDateTime fechaApertura, LocalDateTime fechaCierre, double horasMotorAlAbrir,
                              String codigoMecanico, String observaciones) {
        this.numero = numero;
        this.numeroKart = numeroKart;
        this.tipo = tipo;
        this.estado = estado;
        this.fechaApertura = fechaApertura;
        this.fechaCierre = fechaCierre;
        this.horasMotorAlAbrir = horasMotorAlAbrir;
        this.codigoMecanico = codigoMecanico;
        this.observaciones = observaciones;
    }

    /** Creador (GRASP): la orden preventiva nace del kart que superó el umbral. */
    public static OrdenMantenimiento preventiva(String numero, Kart kart, LocalDateTime ahora) {
        kart.bloquear();
        return new OrdenMantenimiento(numero, kart.getNumeroKart(), TipoMantenimiento.PREVENTIVO,
                EstadoOrden.ABIERTA, ahora, null, kart.getHorasMotor(), null, null);
    }

    public void asignar(Mecanico mecanico) {
        if (estado == EstadoOrden.CERRADA) throw new ConflictoException("La orden " + numero + " ya está cerrada");
        if (!mecanico.puedeAtender(tipo)) throw new ReglaNegocioException("El mecánico no atiende órdenes " + tipo);
        this.codigoMecanico = mecanico.getCodigoEmpleado();
        this.estado = EstadoOrden.EN_PROCESO;
    }

    /** RN-10: solo el mecánico asignado cierra la orden; el kart vuelve a estar disponible. */
    public void cerrar(String codigoMecanicoQueCierra, String observaciones, Kart kart, LocalDateTime ahora) {
        if (estado == EstadoOrden.CERRADA) throw new ConflictoException("La orden " + numero + " ya está cerrada");
        if (codigoMecanico == null || !codigoMecanico.equals(codigoMecanicoQueCierra)) {
            throw new ConflictoException("Solo el mecánico asignado puede cerrar la orden");
        }
        if (kart.getNumeroKart() != numeroKart) throw new ReglaNegocioException("La orden no corresponde al kart");
        this.observaciones = observaciones;
        this.fechaCierre = ahora;
        this.estado = EstadoOrden.CERRADA;
        kart.habilitar();
    }

    public boolean estaAbierta() { return estado != EstadoOrden.CERRADA; }
    public String getNumero() { return numero; }
    public int getNumeroKart() { return numeroKart; }
    public TipoMantenimiento getTipo() { return tipo; }
    public EstadoOrden getEstado() { return estado; }
    public LocalDateTime getFechaApertura() { return fechaApertura; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public double getHorasMotorAlAbrir() { return horasMotorAlAbrir; }
    public String getCodigoMecanico() { return codigoMecanico; }
    public String getObservaciones() { return observaciones; }
}
