package com.MisMascotas.backend.DTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record MascotaResponseDTO(
    UUID idMascota,
    String nombre,
    String especie,
    String raza,
    LocalDate fechaNacimiento,
    boolean fechaAproximada,
    Integer edadValor,
    String edadUnidad,
    String urlFoto,
    String notas,
    UUID propietarioId,
    boolean eliminada,
    LocalDateTime fechaEliminacion
) {}
