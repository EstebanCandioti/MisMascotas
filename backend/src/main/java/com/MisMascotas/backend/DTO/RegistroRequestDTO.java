package com.MisMascotas.backend.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroRequestDTO(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es valido")
        String email,

        @NotBlank(message = "La contrasenia es obligatoria")
        @Size(min = 8, message = "La contrasenia debe tener al menos 8 caracteres")
        @Pattern(regexp = ".*[A-Z].*", message = "La contrasenia debe tener al menos una mayuscula")
        @Pattern(regexp = ".*\\d.*", message = "La contrasenia debe tener al menos un numero")
        String password) {
}