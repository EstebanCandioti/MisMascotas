package com.MisMascotas.backend.DTO;

import java.util.UUID;

public record BadgeResponseDTO(
    UUID idBadge,
    UUID mascotaId,
    String texto,
    String emoji
) {}
