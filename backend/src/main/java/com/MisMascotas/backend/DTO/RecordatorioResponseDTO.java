package com.MisMascotas.backend.DTO;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

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
    UUID confirmadoPorId,
    Instant confirmadoEn,
    String estado,
    String situacionTemporal,
    boolean puedeConfirmar,
    @JsonInclude(JsonInclude.Include.NON_NULL) UUID idOcurrencia,
    @JsonInclude(JsonInclude.Include.NON_NULL) Instant fechaProgramada
) {}
