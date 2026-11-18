package com.tienda.comandos.api;

import com.tienda.comandos.dominio.AccesoDenegadoException;
import com.tienda.comandos.dominio.RecursoNoEncontradoException;
import com.tienda.comandos.dominio.ReglaNegocioException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns exceptions into HTTP errors with the standard "Problem Details" format (RFC 7807):
 * {"status": 409, "title": "...", "detail": "..."}. The clients show {@code detail} to the user.
 */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    /** Shoe or order not found -> 404. */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail noEncontrado(RecursoNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
    }

    /** Business rule violated (e.g. not enough stock) -> 409. */
    @ExceptionHandler(ReglaNegocioException.class)
    public ProblemDetail reglaNegocio(ReglaNegocioException ex) {
        return problema(HttpStatus.CONFLICT, "Regla de negocio", ex.getMessage());
    }

    /** Authenticated but not the owner (e.g. cancelling another customer's order) -> 403. */
    @ExceptionHandler(AccesoDenegadoException.class)
    public ProblemDetail accesoDenegado(AccesoDenegadoException ex) {
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", ex.getMessage());
    }

    /** Two requests changed the same row at the same time (@Version detected it). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail concurrencia(ObjectOptimisticLockingFailureException ex) {
        return problema(HttpStatus.CONFLICT, "Conflicto de concurrencia",
                "Otro usuario modificó este registro al mismo tiempo. Intenta de nuevo.");
    }

    /** Bean Validation errors (@NotBlank, @Positive...) -> 400 with one message per field. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos",
                errores.values().stream().collect(Collectors.joining(". ")));
        problema.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(problema);
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}
