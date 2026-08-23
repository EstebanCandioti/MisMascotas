package com.MisMascotas.backend.Exception;

import java.time.Instant;

public record ErrorResponse(
        String codigo,
        String mensaje,
        Instant timestamp) {
}