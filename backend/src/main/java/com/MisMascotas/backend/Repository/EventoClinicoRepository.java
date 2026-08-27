package com.MisMascotas.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.MisMascotas.backend.Entity.EventoClinico;

@Repository
public interface EventoClinicoRepository extends JpaRepository<EventoClinico, UUID> {

    List<EventoClinico> findByMascotaIdMascotaAndFechaEliminacionIsNullOrderByFechaDesc(UUID mascotaId);

    List<EventoClinico> findByMascotaIdMascotaAndTipoAndFechaEliminacionIsNullOrderByFechaDesc(UUID mascotaId, String tipo);

    Optional<EventoClinico> findByIdEventoAndFechaEliminacionIsNull(UUID idEvento);
}