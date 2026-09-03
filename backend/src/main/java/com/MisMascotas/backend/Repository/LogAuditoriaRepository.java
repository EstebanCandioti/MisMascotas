package com.MisMascotas.backend.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.MisMascotas.backend.Entity.LogAuditoria;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;

public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, UUID>, JpaSpecificationExecutor<LogAuditoria> {
    List<LogAuditoria> findByEntidadAfectadaAndIdEntidadOrderByFechaHoraDesc(String entidadAfectada, UUID idEntidad);

    List<LogAuditoria> findByActor_IdUsuarioOrderByFechaHoraDesc(UUID actorId);

    List<LogAuditoria> findByEntidadAfectadaAndTipoAccionAndFechaHoraBetweenOrderByFechaHoraDesc(
            String entidadAfectada,
            TipoAccionAuditoria tipoAccion,
            Instant desde,
            Instant hasta);
}