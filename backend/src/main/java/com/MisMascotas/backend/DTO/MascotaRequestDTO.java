package com.MisMascotas.backend.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record MascotaRequestDTO(
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "No puede superar los 100 caracteres")
    String nombre,

    @NotBlank(message = "La especie es obligatoria")
    @Size(max = 50, message = "No puede superar los 50 caracteres")
    String especie,

    @Size(max = 100, message = "No puede superar los 100 caracteres")
    String raza,

    @PastOrPresent(message = "La fecha de nacimiento no puede ser futura")
    LocalDate fechaNacimiento,

    boolean fechaAproximada,

    @PositiveOrZero(message = "La edad no puede ser negativa")
    Integer edadValor,

    @Pattern(
        regexp = "dias|semanas|meses|anios",
        message = "La unidad de edad debe ser dias, semanas, meses o anios"
    )
    String edadUnidad,

    String fotoPerfil,

    @DecimalMin(value = "0.01", message = "El peso debe ser mayor o igual a 0.01 kg")
    @DecimalMax(value = "9999.99", message = "El peso debe ser menor o igual a 9999.99 kg")
    BigDecimal pesoActual,

    @Size(max = 255, message = "No puede superar los 255 caracteres")
    String notas
) {}
