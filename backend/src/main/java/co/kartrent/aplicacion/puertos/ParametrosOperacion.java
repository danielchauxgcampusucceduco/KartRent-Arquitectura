package co.kartrent.aplicacion.puertos;

import java.time.Duration;

/** Parámetros configurables del kartódromo (valores de ejemplo a validar con el cliente). */
public interface ParametrosOperacion {
    double umbralHorasPreventivo();
    void actualizarUmbralHoras(double horas);
    Duration tiempoMinimoVuelta();
    Duration retencionReserva();
    double longitudPistaMetros();
    String nitEmisor();
}
