package com.MisMascotas.backend.Exception;

public class ValidacionRequestException extends RuntimeException {
    public ValidacionRequestException(String mensaje) {
        super(mensaje);
    }
}