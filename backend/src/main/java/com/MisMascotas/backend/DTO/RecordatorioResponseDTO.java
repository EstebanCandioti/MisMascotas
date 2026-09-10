package com.MisMascotas.backend.DTO;

import java.time.Instant;
import java.util.UUID;

public record RecordatorioResponseDTO(
    UUID idRecordatorio,
    UUID mascotaId,
    String titulo,
    String tipo,
    Instant fechaHoraInicio,
    String modalidad,
    Integer intervaloValor,
    String intervaloUnidad,
    String diaSemana,
    Instant fechaFin,
    UUID creadoPorId,
    Instant creadoEn,
    String estado
) {}
