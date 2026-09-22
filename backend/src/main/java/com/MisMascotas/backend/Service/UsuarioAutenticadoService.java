package com.MisMascotas.backend.Service;

import java.security.Principal;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.UsuarioRepository;

@Service
public class UsuarioAutenticadoService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioAutenticadoService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public UUID obtenerIdUsuario(Principal principal) {
        return obtenerUsuario(principal).getIdUsuario();
    }

    public Usuario obtenerUsuario(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BadCredentialsException("Usuario autenticado no disponible");
        }

        String email = principal.getName().trim().toLowerCase();
        Usuario usuario = usuarioRepository.findByEmail(email);
        if (usuario == null) {
            throw new RecursoNoEncontradoException("Usuario autenticado no encontrado");
        }

        return usuario;
    }
}
