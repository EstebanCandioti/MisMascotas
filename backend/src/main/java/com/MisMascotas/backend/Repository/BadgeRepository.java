package com.MisMascotas.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.MisMascotas.backend.Entity.Badge;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, UUID> {

    List<Badge> findByMascotaIdMascotaAndFechaEliminacionIsNull(UUID mascotaId);

    Optional<Badge> findByIdBadgeAndFechaEliminacionIsNull(UUID idBadge);
}