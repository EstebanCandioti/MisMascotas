package com.MisMascotas.backend.Exception;

public class PremiumRequeridoException extends RuntimeException {
    public PremiumRequeridoException() {
        super("Esta operacion requiere Premium vigente");
    }
}
