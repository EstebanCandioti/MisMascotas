package com.MisMascotas.backend.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

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
    String notas,

    @JsonIgnore
    boolean fotoPerfilInformada
) {
    public MascotaRequestDTO {
        // Un valor de texto implica presencia, también en llamadas Java internas.
        if (fotoPerfil != null) fotoPerfilInformada = true;
    }

    /** Compatibilidad con llamadas Java: el argumento fotoPerfil está informado, incluso si es null. */
    public MascotaRequestDTO(String nombre, String especie, String raza, LocalDate fechaNacimiento,
            boolean fechaAproximada, Integer edadValor, String edadUnidad, String fotoPerfil,
            BigDecimal pesoActual, String notas) {
        this(nombre, especie, raza, fechaNacimiento, fechaAproximada, edadValor, edadUnidad,
                fotoPerfil, pesoActual, notas, true);
    }

    /** Conserva el JSON público; el indicador de presencia es interno y no puede enviarlo el cliente. */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static MascotaRequestDTO desdeJson(
            @JsonProperty("nombre") String nombre,
            @JsonProperty("especie") String especie,
            @JsonProperty("raza") String raza,
            @JsonProperty("fechaNacimiento") LocalDate fechaNacimiento,
            @JsonProperty("fechaAproximada") boolean fechaAproximada,
            @JsonProperty("edadValor") Integer edadValor,
            @JsonProperty("edadUnidad") String edadUnidad,
            @JsonProperty("fotoPerfil") JsonNode fotoPerfil,
            @JsonProperty("pesoActual") BigDecimal pesoActual,
            @JsonProperty("notas") String notas) {
        if (fotoPerfil != null && !fotoPerfil.isNull() && !fotoPerfil.isTextual()) {
            throw new IllegalArgumentException("La foto de perfil debe ser una URL de texto o null");
        }
        return new MascotaRequestDTO(nombre, especie, raza, fechaNacimiento, fechaAproximada,
                edadValor, edadUnidad, fotoPerfil == null || fotoPerfil.isNull() ? null : fotoPerfil.asText(),
                pesoActual, notas, fotoPerfil != null);
    }
}
