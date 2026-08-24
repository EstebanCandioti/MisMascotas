package com.MisMascotas.backend.DTO;

import java.util.UUID;

public record LoginResponseDTO(
        UUID usuarioId,
        String mensaje) {
}