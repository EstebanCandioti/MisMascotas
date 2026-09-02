package com.MisMascotas.backend.DTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MascotaResponseDTO(
    UUID idMascota,
    UUID propietarioId,
    String nombre,
    String especie,
    String raza,
    LocalDate fechaNacimiento,
    boolean fechaAproximada,
    Integer edadValor,
    String edadUnidad,
    String fotoPerfil,
    BigDecimal pesoActual,
    String notas,
    UUID estadoId,
    Instant creadoEn
) {}