package co.kartrent.dominio.flota;

import co.kartrent.dominio.comun.ReglaNegocioException;

/**
 * Experto en su propio desgaste (GRASP). Abstracta: la edad mínima depende del tipo
 * y se resuelve por polimorfismo en {@link KartAdulto} y {@link KartInfantil}.
 */
public abstract class Kart {
    private final int numeroKart;
    private EstadoKart estado;
    private double kilometraje;
    private double horasMotor;
    private double horasDesdeUltimoServicio;
    private String codigoTransponder;

    protected Kart(int numeroKart, EstadoKart estado, double kilometraje, double horasMotor,
                   double horasDesdeUltimoServicio, String codigoTransponder) {
        if (numeroKart <= 0) throw new ReglaNegocioException("Número de kart inválido");
        this.numeroKart = numeroKart;
        this.estado = estado == null ? EstadoKart.DISPONIBLE : estado;
        this.kilometraje = kilometraje;
        this.horasMotor = horasMotor;
        this.horasDesdeUltimoServicio = horasDesdeUltimoServicio;
        this.codigoTransponder = codigoTransponder;
    }

    public static Kart reconstruir(String tipo, int numero, EstadoKart estado, double km, double horas,
                                   double horasServicio, String transponder) {
        return switch (tipo) {
            case KartAdulto.TIPO -> new KartAdulto(numero, estado, km, horas, horasServicio, transponder);
            case KartInfantil.TIPO -> new KartInfantil(numero, estado, km, horas, horasServicio, transponder);
            default -> throw new ReglaNegocioException("Tipo de kart desconocido: " + tipo);
        };
    }

    public abstract int edadMinimaPiloto();
    public abstract String tipo();

    public boolean estaDisponible() {
        return estado == EstadoKart.DISPONIBLE;
    }

    /** RN-04: solo un kart disponible sale a pista. */
    public void ponerEnPista() {
        if (!estaDisponible()) {
            throw new ReglaNegocioException("Kart " + numeroKart + " no disponible");
        }
        estado = EstadoKart.EN_PISTA;
    }

    /** Acumula el uso al cerrar una tanda y devuelve el kart a boxes. */
    public void registrarUso(int minutos, double km) {
        if (minutos < 0 || km < 0) throw new ReglaNegocioException("El uso no puede ser negativo");
        double horas = minutos / 60.0;
        horasMotor = redondear(horasMotor + horas);
        horasDesdeUltimoServicio = redondear(horasDesdeUltimoServicio + horas);
        kilometraje = redondear(kilometraje + km);
        if (estado == EstadoKart.EN_PISTA) estado = EstadoKart.DISPONIBLE;
    }

    public boolean requiereMantenimiento(double umbralHoras) {
        return horasDesdeUltimoServicio >= umbralHoras;
    }

    public void bloquear() {
        estado = EstadoKart.EN_MANTENIMIENTO;
    }

    public void habilitar() {
        horasDesdeUltimoServicio = 0;
        estado = EstadoKart.DISPONIBLE;
    }

    public void vincularTransponder(String codigo) {
        this.codigoTransponder = codigo;
    }

    private static double redondear(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    public int getNumeroKart() { return numeroKart; }
    public EstadoKart getEstado() { return estado; }
    public double getKilometraje() { return kilometraje; }
    public double getHorasMotor() { return horasMotor; }
    public double getHorasDesdeUltimoServicio() { return horasDesdeUltimoServicio; }
    public String getCodigoTransponder() { return codigoTransponder; }
}
