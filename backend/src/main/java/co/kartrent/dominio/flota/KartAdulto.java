package co.kartrent.dominio.flota;

public class KartAdulto extends Kart {
    public KartAdulto(int numero, EstadoKart estado, double km, double horas, double horasServicio, String transponder) {
        super(numero, estado, km, horas, horasServicio, transponder);
    }

    @Override public TipoKart tipo() { return TipoKart.ADULTO; }
}
