package com.MisMascotas.backend.DTO;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VerificarCodigoRequestDTO(
        @NotNull(message = "El usuario es obligatorio")
        UUID usuarioId,

        @NotBlank(message = "El codigo es obligatorio")
        String codigo) {
}