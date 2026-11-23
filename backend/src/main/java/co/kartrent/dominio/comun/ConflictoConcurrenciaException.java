package co.kartrent.dominio.comun;

/**
 * Otra operación modificó el mismo agregado entre su lectura y su escritura (bloqueo optimista).
 * Es un 409; los casos de uso que lo permiten se reintentan solos con la unidad de trabajo.
 */
public class ConflictoConcurrenciaException extends ConflictoException {
    public ConflictoConcurrenciaException(String mensaje) {
        super(mensaje);
    }
}
