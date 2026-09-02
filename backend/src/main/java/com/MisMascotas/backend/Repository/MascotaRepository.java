package com.MisMascotas.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.MisMascotas.backend.Entity.Mascota;

@Repository
public interface MascotaRepository extends JpaRepository<Mascota, UUID> {

    List<Mascota> findByPropietarioIdAndFechaEliminacionIsNull(UUID propietarioId);

    Optional<Mascota> findByIdMascotaAndFechaEliminacionIsNull(UUID idMascota);
}
