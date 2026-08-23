package com.MisMascotas.backend.Service;

import org.springframework.stereotype.Service;

import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Repository.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Usuario buscarPorEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }

        return usuarioRepository.findByEmail(email.trim().toLowerCase());
    }
}