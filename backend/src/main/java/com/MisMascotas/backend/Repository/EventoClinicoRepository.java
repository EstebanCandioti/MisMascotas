package com.MisMascotas.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.MisMascotas.backend.Entity.EventoClinico;

@Repository
public interface EventoClinicoRepository extends JpaRepository<EventoClinico, UUID> {

    @Query("SELECT e FROM EventoClinico e WHERE e.mascota.idMascota = :mascotaId AND e.fechaEliminacion IS NULL ORDER BY e.fecha DESC")
    List<EventoClinico> findByMascotaIdOrderByFechaDesc(@Param("mascotaId") UUID mascotaId);

    @Query("SELECT e FROM EventoClinico e WHERE e.mascota.idMascota = :mascotaId AND e.tipo = :tipo AND e.fechaEliminacion IS NULL ORDER BY e.fecha DESC")
    List<EventoClinico> findByMascotaIdAndTipoOrderByFechaDesc(@Param("mascotaId") UUID mascotaId, @Param("tipo") String tipo);

    Optional<EventoClinico> findByIdEventoAndFechaEliminacionIsNull(UUID idEvento);
}