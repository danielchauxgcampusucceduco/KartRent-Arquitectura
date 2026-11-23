package co.kartrent.dominio.comun;

/** Violación de una regla de negocio del kartódromo (RN-xx). */
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
