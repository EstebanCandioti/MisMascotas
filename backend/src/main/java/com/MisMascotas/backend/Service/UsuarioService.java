package com.MisMascotas.backend.Service;

import java.security.Principal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.DTO.UsuarioResponseDTO;
import com.MisMascotas.backend.Entity.Usuario;

@Service
public class UsuarioService {

    private final UsuarioAutenticadoService usuarioAutenticadoService;
    private final PremiumService premiumService;

    public UsuarioService(UsuarioAutenticadoService usuarioAutenticadoService, PremiumService premiumService) {
        this.usuarioAutenticadoService = usuarioAutenticadoService;
        this.premiumService = premiumService;
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtenerPerfilAutenticado(Principal principal) {
        Usuario usuario = usuarioAutenticadoService.obtenerUsuario(principal);
        return new UsuarioResponseDTO(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getEmail(),
                premiumService.esPremium(usuario.getIdUsuario())
        );
    }
}
