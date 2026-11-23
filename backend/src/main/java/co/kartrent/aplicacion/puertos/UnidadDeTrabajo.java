package co.kartrent.aplicacion.puertos;

import co.kartrent.dominio.comun.ConflictoConcurrenciaException;

import java.util.function.Supplier;

/** Abstrae la transacción para que los casos de uso no dependan de Spring. */
public interface UnidadDeTrabajo {
    int MAXIMO_INTENTOS = 10;

    <T> T ejecutar(Supplier<T> trabajo);

    default void ejecutar(Runnable trabajo) {
        ejecutar(() -> {
            trabajo.run();
            return null;
        });
    }

    /**
     * Ejecuta el trabajo en una transacción nueva y lo repite si otra transacción modificó el mismo agregado
     * (bloqueo optimista). Cada fallo implica que otra operación confirmó un cambio, así que el trabajo
     * siempre avanza; si aun así se agotan los intentos, el conflicto sube como 409.
     */
    default <T> T ejecutarConReintentos(Supplier<T> trabajo) {
        for (int intento = 1; ; intento++) {
            try {
                return ejecutar(trabajo);
            } catch (ConflictoConcurrenciaException e) {
                if (intento >= MAXIMO_INTENTOS) throw e;
            }
        }
    }

    UnidadDeTrabajo DIRECTA = new UnidadDeTrabajo() {
        @Override
        public <T> T ejecutar(Supplier<T> trabajo) {
            return trabajo.get();
        }
    };
}
