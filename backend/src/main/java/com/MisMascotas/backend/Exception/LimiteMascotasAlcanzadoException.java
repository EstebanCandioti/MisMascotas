package com.MisMascotas.backend.Exception;

public class LimiteMascotasAlcanzadoException extends RuntimeException {
    public LimiteMascotasAlcanzadoException(String mensaje) {
        super(mensaje);
    }
}