package com.MisMascotas.backend.Service;

import java.security.Principal;

import org.springframework.stereotype.Service;

import com.MisMascotas.backend.DTO.UsuarioResponseDTO;
import com.MisMascotas.backend.Entity.Usuario;

@Service
public class UsuarioService {

    private final UsuarioAutenticadoService usuarioAutenticadoService;

    public UsuarioService(UsuarioAutenticadoService usuarioAutenticadoService) {
        this.usuarioAutenticadoService = usuarioAutenticadoService;
    }

    public UsuarioResponseDTO obtenerPerfilAutenticado(Principal principal) {
        Usuario usuario = usuarioAutenticadoService.obtenerUsuario(principal);
        return new UsuarioResponseDTO(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.isEsPremium()
        );
    }
}
