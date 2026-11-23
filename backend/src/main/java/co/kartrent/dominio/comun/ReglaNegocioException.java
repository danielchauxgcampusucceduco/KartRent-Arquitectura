package co.kartrent.dominio.comun;

/**
 * Los datos de la petición incumplen una regla de negocio (RN-xx): edad mínima, exoneración sin firmar, valores
 * fuera de rango. Se responde 400. Los choques con el estado actual usan {@link ConflictoException} (409).
 */
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
