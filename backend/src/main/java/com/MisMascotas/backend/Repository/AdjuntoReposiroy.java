package com.MisMascotas.backend.Repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.MisMascotas.backend.Entity.Adjunto;

public interface AdjuntoReposiroy extends JpaRepository<Adjunto, UUID> {
    // Aquí puedes agregar métodos de consulta personalizados si es necesario
    
}
