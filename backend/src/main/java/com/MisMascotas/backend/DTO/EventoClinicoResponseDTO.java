package com.MisMascotas.backend.DTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EventoClinicoResponseDTO(
    UUID idEvento,
    UUID mascotaId,
    UUID registradoPorId,
    String registradoPorNombre,
    String tipo,
    Instant fecha,
    String nombre,
    String dosis,
    BigDecimal valorPeso,
    String motivo,
    String diagnostico,
    String observaciones,
    Instant creadoEn
) {}