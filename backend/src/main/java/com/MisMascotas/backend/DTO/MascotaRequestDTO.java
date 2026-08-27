package com.MisMascotas.backend.DTO;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;

public record MascotaRequestDTO(
    @NotBlank(message = "El nombre es obligatorio")
    String nombre,

    @NotBlank(message = "La especie es obligatoria")
    String especie,

    LocalDate fechaNacimiento,

    boolean fechaAproximada,

    Integer edadValor,

    String edadUnidad,

    String raza,

    String notas
) {}