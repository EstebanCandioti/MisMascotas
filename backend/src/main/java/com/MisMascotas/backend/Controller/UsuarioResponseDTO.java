package com.MisMascotas.backend.Controller;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioResponseDTO {
    private UUID idUsuario;
    private String nombre;
    private String email;
    private boolean esPremium;
    private boolean activo;
    private Instant creadoEn;
}
