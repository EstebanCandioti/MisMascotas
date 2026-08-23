package com.MisMascotas.backend.DTO;

public record AuthResponseDTO(
        String token,
        String tipo) {

    public AuthResponseDTO(String token) {
        this(token, "Bearer");
    }
}