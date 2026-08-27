package com.MisMascotas.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.MisMascotas.backend.Entity.Recordatorio;

@Repository
public interface RecordatorioRepository extends JpaRepository<Recordatorio, UUID> {

    List<Recordatorio> findByMascotaIdMascotaAndFechaEliminacionIsNull(UUID mascotaId);

    List<Recordatorio> findByCreadoPorIdAndFechaEliminacionIsNull(UUID usuarioId);

    Optional<Recordatorio> findByIdRecordatorioAndFechaEliminacionIsNull(UUID idRecordatorio);
}
