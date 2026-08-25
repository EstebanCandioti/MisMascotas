package com.MisMascotas.backend.Exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleRecursoNoEncontradoException(RecursoNoEncontradoException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", ex.getMessage());
    }

    @ExceptionHandler(CodigoExpiradoException.class)
    public ResponseEntity<ErrorResponse> handleCodigoExpiradoException(CodigoExpiradoException ex) {
        return buildResponse(HttpStatus.GONE, "CODIGO_EXPIRADO", ex.getMessage());
    }

    @ExceptionHandler(CodigoInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleCodigoInvalidoException(CodigoInvalidoException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "CODIGO_INVALIDO", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        HttpStatus status = ex.getMessage() != null && ex.getMessage().contains("ya esta registrado")
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;

        return buildResponse(status, "ERROR_NEGOCIO", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> erroresPorCampo = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            erroresPorCampo.putIfAbsent(error.getField(), formatearErrorCampo(error));
        }

        String mensaje = erroresPorCampo.values()
                .stream()
                .collect(Collectors.joining("; "));

        return buildResponse(HttpStatus.BAD_REQUEST, "ERROR_VALIDACION", mensaje);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "Credenciales invalidas");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "No tenes permisos para realizar esta accion");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Ocurrio un error inesperado");
    }

    private String formatearErrorCampo(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String codigo, String mensaje) {
        ErrorResponse error = new ErrorResponse(codigo, mensaje, Instant.now());
        return ResponseEntity.status(status).body(error);
    }
}