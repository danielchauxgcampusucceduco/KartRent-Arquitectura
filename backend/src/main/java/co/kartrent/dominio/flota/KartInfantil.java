package co.kartrent.dominio.flota;

public class KartInfantil extends Kart {
    public KartInfantil(int numero, EstadoKart estado, double km, double horas, double horasServicio, String transponder) {
        super(numero, estado, km, horas, horasServicio, transponder);
    }

    @Override public TipoKart tipo() { return TipoKart.INFANTIL; }

    /** Un kart infantil tampoco puede asignarse a un adulto (RN-05). */
    public int edadMaximaPiloto() { return tipo().edadMaxima(); }
}
