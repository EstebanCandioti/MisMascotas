package com.MisMascotas.backend.DTO;

import java.util.UUID;

public record UsuarioResponseDTO(
        UUID idUsuario,
        String nombre,
        String email,
        boolean esPremium
) {
}
