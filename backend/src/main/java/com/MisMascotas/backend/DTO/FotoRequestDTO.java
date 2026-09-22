package com.MisMascotas.backend.DTO;

import jakarta.validation.constraints.NotBlank;

public record FotoRequestDTO(
    @NotBlank(message = "La URL del archivo es obligatoria")
    String urlArchivo,

    String formato
) {}
