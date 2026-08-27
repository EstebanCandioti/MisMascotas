package com.MisMascotas.backend.DTO;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecordatorioRequestDTO(
    @NotNull(message = "El ID de la mascota es obligatorio")
    UUID mascotaId,

    @NotBlank(message = "El título es obligatorio")
    String titulo,

    @NotBlank(message = "El tipo es obligatorio")
    String tipo,

    @NotNull(message = "La fecha y hora de inicio son obligatorias")
    Instant fechaHoraInicio,

    @NotBlank(message = "La modalidad es obligatoria")
    String modalidad,

    Integer intervaloValor,

    String intervaloUnidad,

    String diasSemana,

    Instant fechaFin
) {}
