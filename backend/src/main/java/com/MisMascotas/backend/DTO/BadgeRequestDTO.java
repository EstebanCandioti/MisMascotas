package com.MisMascotas.backend.DTO;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BadgeRequestDTO(
    @NotNull(message = "El ID de la mascota es obligatorio")
    UUID mascotaId,

    @NotBlank(message = "El texto es obligatorio")
    String texto,

    String emoji
) {}
