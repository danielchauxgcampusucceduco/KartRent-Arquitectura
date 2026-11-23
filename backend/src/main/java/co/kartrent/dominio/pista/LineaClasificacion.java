package co.kartrent.dominio.pista;

public record LineaClasificacion(int posicion, int numeroKart, String piloto, int vueltas,
                                 Long mejorVueltaMs, Long ultimaVueltaMs) {
}
