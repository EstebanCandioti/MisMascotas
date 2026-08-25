package com.MisMascotas.backend.DTO;

import jakarta.validation.constraints.NotBlank;

public record VerificarCodigoRequestDTO(
        @NotBlank(message = "El codigo es obligatorio")
        String codigo) {
}