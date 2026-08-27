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
    String diasSemana,
    Instant fechaFin,
    String estado,
    UUID creadoPorId,
    UUID confirmadoPorId,
    Instant confirmadoEn
) {}
