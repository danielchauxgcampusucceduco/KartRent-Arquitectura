package co.kartrent.dominio.comun;

/** La petición no trae credenciales válidas (por ejemplo, la firma del webhook no coincide). Se responde 401. */
public class NoAutorizadoException extends RuntimeException {
    public NoAutorizadoException(String mensaje) {
        super(mensaje);
    }
}
