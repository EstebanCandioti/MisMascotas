package com.MisMascotas.backend.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AlbumRequestDTO(
    @NotBlank(message = "El nombre es obligatorio")
    String nombre,

    String descripcion,

    @NotNull(message = "El ID de la mascota es obligatorio")
    UUID mascotaId
) {}