package com.MisMascotas.backend.Exception;

public class EstadoNoConfiguradoException extends RuntimeException {

    public EstadoNoConfiguradoException(String entidad, String nombre) {
        super("Estado no configurado para entidad '" + entidad + "' y nombre '" + nombre + "'");
    }
}