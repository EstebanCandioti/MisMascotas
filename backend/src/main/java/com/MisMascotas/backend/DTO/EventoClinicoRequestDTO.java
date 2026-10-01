package com.MisMascotas.backend.DTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EventoClinicoRequestDTO(
    UUID mascotaId,

    @NotBlank(message = "El tipo de evento es obligatorio")
    String tipo,

    @NotNull(message = "La fecha es obligatoria")
    Instant fecha,

    String nombre,
    String dosis,
    BigDecimal valorPeso,
    String motivo,
    String diagnostico,
    String observaciones
) {}
