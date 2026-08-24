package com.MisMascotas.backend.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.MisMascotas.backend.DTO.UsuarioResponseDTO;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Service.UsuarioService;

/**
 * CU10 - Ver configuracion. Endpoint autenticado (requiere JWT valido,
 * validado por JwtFilter, que deja el UserDetails en el SecurityContext).
 */
@RestController
@RequestMapping("/usuario")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponseDTO> obtenerPerfil(@AuthenticationPrincipal UserDetails userDetails) {
        // El "username" de Spring Security es el email (subject del JWT),
        // cargado por CustomUserDetailsService.
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        if (usuario == null) {
            throw new RecursoNoEncontradoException("Usuario no encontrado");
        }

        UsuarioResponseDTO respuesta = UsuarioResponseDTO.builder()
                .idUsuario(usuario.getIdUsuario())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .esPremium(usuario.isEsPremium())
                .activo(usuario.isActivo())
                .creadoEn(usuario.getCreadoEn())
                .build();

        return ResponseEntity.ok(respuesta);
    }
}