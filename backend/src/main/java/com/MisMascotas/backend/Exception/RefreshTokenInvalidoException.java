package com.MisMascotas.backend.Exception;

public class RefreshTokenInvalidoException extends RuntimeException {
    public RefreshTokenInvalidoException(String mensaje) {
        super(mensaje);
    }
}
