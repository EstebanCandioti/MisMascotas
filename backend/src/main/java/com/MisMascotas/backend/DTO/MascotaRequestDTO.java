package com.MisMascotas.backend.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record MascotaRequestDTO(
    @NotBlank(message = "El nombre es obligatorio")
    String nombre,

    @NotBlank(message = "La especie es obligatoria")
    String especie,

    String raza,
    LocalDate fechaNacimiento,
    boolean fechaAproximada,
    Integer edadValor,
    String edadUnidad,
    String fotoPerfil,
    BigDecimal pesoActual,
    String notas,
    UUID estadoId
) {}