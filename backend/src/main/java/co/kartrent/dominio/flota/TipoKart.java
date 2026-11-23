package co.kartrent.dominio.flota;

/** Tipo de kart y tanda. Fuente única de las edades permitidas (RN-02, RN-05). */
public enum TipoKart {
    ADULTO(16, Integer.MAX_VALUE),
    INFANTIL(8, 15);

    private final int edadMinima;
    private final int edadMaxima;

    TipoKart(int edadMinima, int edadMaxima) {
        this.edadMinima = edadMinima;
        this.edadMaxima = edadMaxima;
    }

    public int edadMinima() {
        return edadMinima;
    }

    public int edadMaxima() {
        return edadMaxima;
    }
}
