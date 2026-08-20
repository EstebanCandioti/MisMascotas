package com.MisMascotas.backend.Service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Repository.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository userRepo;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.userRepo = usuarioRepository;
    }

    public Usuario registrar(String nombre, String email, String password) {
        validarDatosRegistro(nombre, email, password);

        String emailNormalizado = email.trim().toLowerCase();
        if (userRepo.existsByEmail(emailNormalizado)) {
            throw new IllegalArgumentException("El email ya esta registrado");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre.trim());
        usuario.setEmail(emailNormalizado);
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setActivo(true);
        usuario.setEsPremium(false);

        return userRepo.save(usuario);
    }

    public Usuario buscarPorEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }

        return userRepo.findByEmail(email.trim().toLowerCase());
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