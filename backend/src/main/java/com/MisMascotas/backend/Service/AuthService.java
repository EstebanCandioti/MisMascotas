package com.MisMascotas.backend.Service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Repository.UsuarioRepository;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario registrar(String nombre, String email, String password) {
        validarDatosRegistro(nombre, email, password);

        String emailNormalizado = email.trim().toLowerCase();
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            throw new IllegalArgumentException("El email ya esta registrado");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre.trim());
        usuario.setEmail(emailNormalizado);
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setActivo(true);
        usuario.setEsPremium(false);

        return usuarioRepository.save(usuario);
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