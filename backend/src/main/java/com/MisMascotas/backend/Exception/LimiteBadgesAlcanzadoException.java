package com.MisMascotas.backend.Exception;

public class LimiteBadgesAlcanzadoException extends RuntimeException {
    public LimiteBadgesAlcanzadoException() {
        super("Alcanzaste el limite de 3 badges activas por mascota.");
    }
}
