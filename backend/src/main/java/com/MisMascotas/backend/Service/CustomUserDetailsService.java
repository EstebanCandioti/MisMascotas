package com.MisMascotas.backend.Service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Repository.UsuarioRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public CustomUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.trim().isEmpty()) {
            throw new UsernameNotFoundException("El email es obligatorio");
        }

        String emailNormalizado = email.trim().toLowerCase();
        Usuario usuario = usuarioRepository.findByEmail(emailNormalizado);

        if (usuario == null) {
            throw new UsernameNotFoundException("No existe un usuario con el email: " + emailNormalizado);
        }

        return User.builder()
                .username(usuario.getEmail())
                .password(usuario.getPasswordHash())
                .authorities("ROLE_USER")
                .disabled(!usuario.isActivo())
                .accountLocked(false)
                .credentialsExpired(false)
                .accountExpired(false)
                .build();
    }
}