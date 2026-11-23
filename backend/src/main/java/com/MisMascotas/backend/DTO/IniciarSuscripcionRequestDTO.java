package com.MisMascotas.backend.DTO;

import jakarta.validation.constraints.NotBlank;

public record IniciarSuscripcionRequestDTO(
        @NotBlank(message = "El tipo de plan es obligatorio")
        String tipoPlan) {
}
