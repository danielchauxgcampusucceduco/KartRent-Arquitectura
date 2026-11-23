package co.kartrent.presentacion;

import co.kartrent.dominio.comun.ConflictoException;
import co.kartrent.dominio.comun.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.*;

/** Verifica el mapeo de excepciones a códigos HTTP del contrato (incluido el 500, difícil de provocar por la API). */
class ManejadorErroresTest {
    private final ManejadorErrores manejador = new ManejadorErrores();

    @Test
    void unErrorNoPrevistoEsUn500QueNoFiltraDetallesInternos() {
        var r = manejador.inesperado(new IllegalStateException("password=secreto jdbc://interno"));
        assertEquals(500, r.getStatusCode().value());
        assertNotNull(r.getBody());
        assertFalse(r.getBody().getDetail().contains("secreto"));
        assertEquals("Error interno", r.getBody().getTitle());
    }

    @Test
    void conflictoEsUn409YReglaDeNegocioUn400() {
        assertEquals(409, manejador.conflicto(new ConflictoException("Cupos insuficientes")).getStatus());
        assertEquals(400, manejador.reglaNegocio(new ReglaNegocioException("Edad mínima")).getStatus());
    }

    @Test
    void lasViolacionesDeIntegridadYDeConcurrenciaSon409() {
        assertEquals(409, manejador.integridad(new DataIntegrityViolationException("duplicado")).getStatus());
        assertEquals(409, manejador.concurrencia(new OptimisticLockingFailureException("versión")).getStatus());
    }
}
