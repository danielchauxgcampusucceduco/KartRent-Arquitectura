package co.kartrent.dominio.flota;

public class KartInfantil extends Kart {
    public static final String TIPO = "INFANTIL";

    public KartInfantil(int numero, EstadoKart estado, double km, double horas, double horasServicio, String transponder) {
        super(numero, estado, km, horas, horasServicio, transponder);
    }

    @Override public int edadMinimaPiloto() { return 8; }
    @Override public String tipo() { return TIPO; }

    /** Un kart infantil tampoco puede asignarse a un adulto (RN-05). */
    public int edadMaximaPiloto() { return 15; }
}
