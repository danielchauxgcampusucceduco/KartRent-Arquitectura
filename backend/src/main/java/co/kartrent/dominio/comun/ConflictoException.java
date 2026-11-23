package co.kartrent.dominio.comun;

/**
 * La operación es válida en sí misma, pero choca con el estado actual del recurso
 * (cupos agotados, kart ocupado, reserva ya pagada, duplicado, transición inválida). Se responde 409.
 */
public class ConflictoException extends ReglaNegocioException {
    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
