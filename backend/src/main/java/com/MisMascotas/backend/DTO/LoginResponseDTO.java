package com.MisMascotas.backend.DTO;

public record LoginResponseDTO(
        String token,
        String tipo,
        String mensaje) {

    public LoginResponseDTO(String token, String mensaje) {
        this(token, "Bearer", mensaje);
    }
}