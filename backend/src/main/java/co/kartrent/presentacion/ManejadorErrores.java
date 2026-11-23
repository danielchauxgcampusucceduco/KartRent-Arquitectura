package co.kartrent.presentacion;

import co.kartrent.dominio.comun.ConflictoException;
import co.kartrent.dominio.comun.NoAutorizadoException;
import co.kartrent.dominio.comun.RecursoNoEncontradoException;
import co.kartrent.dominio.comun.ReglaNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Traduce excepciones a respuestas RFC 7807 (Problem Details) con el código HTTP del contrato REST:
 * 400 datos inválidos, 401 sin credenciales, 404 no existe, 409 conflicto con el estado actual, 500 error interno.
 */
@RestControllerAdvice
public class ManejadorErrores {
    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(ConflictoException.class)
    ProblemDetail conflicto(ConflictoException e) {
        return problema(HttpStatus.CONFLICT, "Conflicto con el estado actual", e.getMessage());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    ProblemDetail reglaNegocio(ReglaNegocioException e) {
        return problema(HttpStatus.BAD_REQUEST, "Regla de negocio incumplida", e.getMessage());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ProblemDetail noEncontrado(RecursoNoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", e.getMessage());
    }

    @ExceptionHandler(NoAutorizadoException.class)
    ProblemDetail noAutorizado(NoAutorizadoException e) {
        return problema(HttpStatus.UNAUTHORIZED, "No autorizado", e.getMessage());
    }

    /** Un rol correcto pero sin permiso sobre ese recurso (por ejemplo, un mecánico cerrando la orden de otro). */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail prohibido(AccessDeniedException e) {
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacion(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).collect(Collectors.joining("; "));
        return problema(HttpStatus.BAD_REQUEST, "Datos inválidos", detalle);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail cuerpoIlegible(HttpMessageNotReadableException e) {
        return problema(HttpStatus.BAD_REQUEST, "Cuerpo de la petición ilegible",
                "El cuerpo no es un JSON válido o contiene un valor que no corresponde al campo");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametroInvalido(MethodArgumentTypeMismatchException e) {
        return problema(HttpStatus.BAD_REQUEST, "Parámetro inválido", "El parámetro '" + e.getName() + "' no tiene el formato esperado");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridad(DataIntegrityViolationException e) {
        log.warn("Violación de integridad en base de datos: {}", e.getMostSpecificCause().getMessage());
        return problema(HttpStatus.CONFLICT, "Conflicto de datos",
                "La operación viola una restricción de integridad (duplicado o dato relacionado inexistente)");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrencia(OptimisticLockingFailureException e) {
        return problema(HttpStatus.CONFLICT, "Conflicto de concurrencia",
                "Otro usuario modificó el recurso al mismo tiempo; vuelve a intentarlo");
    }

    /** Red de seguridad: cualquier error no previsto es un 500 sin filtrar detalles internos. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> inesperado(Exception e) {
        if (e instanceof ErrorResponse estandar) {
            // Errores propios de Spring MVC (405, 415, ruta inexistente...) conservan su código.
            return ResponseEntity.status(estandar.getStatusCode()).headers(estandar.getHeaders())
                    .body(estandar.getBody());
        }
        log.error("Error no controlado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problema(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno", "Ocurrió un error inesperado; inténtalo de nuevo más tarde"));
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(estado, detalle);
        p.setTitle(titulo);
        return p;
    }
}
