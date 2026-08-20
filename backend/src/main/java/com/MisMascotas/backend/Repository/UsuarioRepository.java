package com.MisMascotas.backend.Repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.MisMascotas.backend.Entity.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Usuario findByEmail(String email);
    boolean existsByEmail(String email);
}