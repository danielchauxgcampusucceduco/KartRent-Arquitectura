package co.kartrent.infraestructura.config;

import co.kartrent.aplicacion.puertos.UnidadDeTrabajo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Cada caso de uso corre en una transacción; la aplicación no depende de Spring. */
@Component
public class UnidadDeTrabajoSpring implements UnidadDeTrabajo {
    private final TransactionTemplate tx;

    public UnidadDeTrabajoSpring(TransactionTemplate tx) {
        this.tx = tx;
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return tx.execute(estado -> trabajo.get());
    }
}
