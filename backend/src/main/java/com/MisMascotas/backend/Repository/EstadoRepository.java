package com.MisMascotas.backend.Repository;

import com.MisMascotas.backend.Entity.Estado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstadoRepository extends JpaRepository<Estado, Integer> {

    Optional<Estado> findByNombreIgnoreCase(String nombre);
}
