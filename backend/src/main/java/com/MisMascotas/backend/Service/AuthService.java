package com.MisMascotas.backend.Service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.MisMascotas.backend.DTO.AuthResponseDTO;
import com.MisMascotas.backend.DTO.RegistroRequestDTO;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Repository.UsuarioRepository;
import com.MisMascotas.backend.Security.JwtService;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponseDTO registrar(RegistroRequestDTO request) {
        validarDatosRegistro(request.nombre(), request.email(), request.password());

        String emailNormalizado = request.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            throw new IllegalArgumentException("El email ya esta registrado");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(emailNormalizado);
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuario.setActivo(true);
        usuario.setEsPremium(false);

        Usuario usuarioGuardado = usuarioRepository.save(usuario);
        String token = jwtService.generarToken(usuarioGuardado);

        return new AuthResponseDTO(token);
    }

    private void validarDatosRegistro(String nombre, String email, String password) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }

        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }

        if (!email.contains("@")) {
            throw new IllegalArgumentException("El email no es valido");
        }

        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("La contrasenia debe tener al menos 8 caracteres");
        }

        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("La contrasenia debe tener al menos una mayuscula");
        }

        if (!password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("La contrasenia debe tener al menos un numero");
        }
    }
}