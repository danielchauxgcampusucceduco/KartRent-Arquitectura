package co.kartrent.dominio.pista;

/** Uso de un kart en una tanda, que Flota acumula al finalizarla (interfaz RegistrarUso). */
public record UsoKart(int numeroKart, int minutos, double kilometros) {
}
