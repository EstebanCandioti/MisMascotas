package com.MisMascotas.backend.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record RecordatorioRequestDTO(
    @NotNull(message = "El ID de la mascota es obligatorio")
    UUID mascotaId,

    @NotBlank(message = "El título es obligatorio")
    String titulo,

    @NotBlank(message = "El tipo es obligatorio")
    String tipo,

    @NotNull(message = "La fecha de inicio es obligatoria")
    Instant fechaHoraInicio,

    String modalidad,

    Integer intervaloValor,

    String intervaloUnidad,

    String diasSemana,

    Instant fechaFin
) {}
