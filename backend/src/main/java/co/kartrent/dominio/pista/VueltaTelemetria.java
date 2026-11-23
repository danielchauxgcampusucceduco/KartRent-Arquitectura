package co.kartrent.dominio.pista;

import java.time.Duration;
import java.time.Instant;

/** Una vuelta medida entre dos pasos consecutivos del transponder por el bucle de meta. */
public record VueltaTelemetria(int numeroVuelta, Instant marcaTiempo, Duration tiempoVuelta) {

    public boolean esValida(Duration tiempoMinimo) {
        return tiempoVuelta.compareTo(tiempoMinimo) >= 0;
    }

    /** Velocidad promedio en km/h dada la longitud del trazado en metros. */
    public double velocidadPromedio(double longitudPistaM) {
        double segundos = tiempoVuelta.toMillis() / 1000.0;
        return segundos == 0 ? 0 : (longitudPistaM / segundos) * 3.6;
    }

    public long milisegundos() {
        return tiempoVuelta.toMillis();
    }
}
