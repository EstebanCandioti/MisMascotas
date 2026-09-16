package com.MisMascotas.backend.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;

public record RecordatorioRequestDTO(
    @NotNull(message = "El ID de la mascota es obligatorio")
    UUID mascotaId,

    @NotBlank(message = "El titulo es obligatorio")
    String titulo,

    @NotBlank(message = "El tipo es obligatorio")
    @Pattern(
        regexp = "(?i)^(PIPETA_ANTIPARASITARIO|VISITA_VETERINARIO|MEDICACION|VACUNA|OTRO)$",
        message = "El tipo debe ser uno de: PIPETA_ANTIPARASITARIO, VISITA_VETERINARIO, MEDICACION, VACUNA, OTRO"
    )
    String tipo,

    @NotNull(message = "La fecha de inicio es obligatoria")
    Instant fechaHoraInicio,

    String modalidad,

    Integer intervaloValor,

    String intervaloUnidad,

    String diasSemana,

    Instant fechaFin
) {}