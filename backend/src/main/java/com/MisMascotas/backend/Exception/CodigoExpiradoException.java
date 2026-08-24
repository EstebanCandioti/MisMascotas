package com.MisMascotas.backend.Exception;

public class CodigoExpiradoException extends RuntimeException {
    public CodigoExpiradoException(String mensaje) {
        super(mensaje);
    }
}