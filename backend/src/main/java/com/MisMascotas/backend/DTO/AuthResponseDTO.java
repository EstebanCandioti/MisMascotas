package com.MisMascotas.backend.DTO;

import java.time.Instant;

public record AuthResponseDTO(
        String accessToken,
        String refreshToken,
        String tipo,
        Instant accessTokenExpiraEn) {
}
