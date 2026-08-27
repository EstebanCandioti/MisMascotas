package com.MisMascotas.backend.DTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EventoClinicoRequestDTO(
    @NotNull(message = "El ID de la mascota es obligatorio")
    UUID mascotaId,

    @NotBlank(message = "El tipo de evento es obligatorio")
    String tipo,

    @NotNull(message = "La fecha es obligatoria")
    Instant fecha,

    String nombre,
    String dosis,
    BigDecimal valorNumerico,
    String motivo,
    String diagnostico,
    String observaciones,
    String urlAdjunto
) {}