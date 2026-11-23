package co.kartrent.aplicacion.puertos;

import java.util.function.Supplier;

/** Abstrae la transacción para que los casos de uso no dependan de Spring. */
public interface UnidadDeTrabajo {
    <T> T ejecutar(Supplier<T> trabajo);

    default void ejecutar(Runnable trabajo) {
        ejecutar(() -> {
            trabajo.run();
            return null;
        });
    }

    UnidadDeTrabajo DIRECTA = new UnidadDeTrabajo() {
        @Override
        public <T> T ejecutar(Supplier<T> trabajo) {
            return trabajo.get();
        }
    };
}
