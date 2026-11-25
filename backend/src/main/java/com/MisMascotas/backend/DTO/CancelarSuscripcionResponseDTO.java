package com.MisMascotas.backend.DTO;

import java.time.Instant;
import java.util.UUID;

public record CancelarSuscripcionResponseDTO(
        UUID idSuscripcion,
        Resultado resultado,
        String estado,
        Instant fechaCancelacion,
        Instant fechaVencimiento) {
    public enum Resultado {
        CANCELADA, YA_CANCELADA, NO_CANCELABLE, CANCELACION_NO_CONFIRMADA, REQUIERE_CONCILIACION
    }

    public static CancelarSuscripcionResponseDTO sinConfirmar(UUID id, Resultado resultado) {
        return new CancelarSuscripcionResponseDTO(id, resultado, null, null, null);
    }
}
