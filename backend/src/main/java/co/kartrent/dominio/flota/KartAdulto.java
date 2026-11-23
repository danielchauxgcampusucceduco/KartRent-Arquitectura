package co.kartrent.dominio.flota;

public class KartAdulto extends Kart {
    public static final String TIPO = "ADULTO";

    public KartAdulto(int numero, EstadoKart estado, double km, double horas, double horasServicio, String transponder) {
        super(numero, estado, km, horas, horasServicio, transponder);
    }

    @Override public int edadMinimaPiloto() { return 16; }
    @Override public String tipo() { return TIPO; }
}
