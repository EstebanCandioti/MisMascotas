<<<<<<< Updated upstream
=======
package com.MisMascotas.backend.DTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EventoClinicoResponseDTO(
    UUID idEvento,
    UUID mascotaId,
    UUID registradoPorId,
    String registradoPorNombre,
    String tipo,
    LocalDate fecha,
    String nombre,
    String dosis,
    BigDecimal valorNumerico,
    String motivo,
    String diagnostico,
    String observaciones,
    String urlAdjunto,
    Instant creadoEn
) {}
>>>>>>> Stashed changes
