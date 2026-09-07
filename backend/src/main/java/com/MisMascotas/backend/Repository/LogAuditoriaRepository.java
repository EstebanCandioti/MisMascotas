package com.MisMascotas.backend.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.MisMascotas.backend.Entity.LogAuditoria;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;

public interface LogAuditoriaRepository extends Repository<LogAuditoria, UUID> {
    LogAuditoria save(LogAuditoria logAuditoria);

    List<LogAuditoria> findByEntidadAfectadaAndIdEntidadOrderByFechaHoraDesc(String entidadAfectada, UUID idEntidad);

    List<LogAuditoria> findByActor_IdUsuarioOrderByFechaHoraDesc(UUID actorId);

    List<LogAuditoria> findByEntidadAfectadaAndTipoAccionAndFechaHoraBetweenOrderByFechaHoraDesc(
            String entidadAfectada,
            TipoAccionAuditoria tipoAccion,
            Instant desde,
            Instant hasta);

    @Query("""
            SELECT log
            FROM LogAuditoria log
            WHERE (:entidadAfectada IS NULL OR LOWER(log.entidadAfectada) = LOWER(:entidadAfectada))
              AND (:tipoAccion IS NULL OR log.tipoAccion = :tipoAccion)
              AND (:desde IS NULL OR log.fechaHora >= :desde)
              AND (:hasta IS NULL OR log.fechaHora <= :hasta)
            """)
    Page<LogAuditoria> buscarConFiltros(
            @Param("entidadAfectada") String entidadAfectada,
            @Param("tipoAccion") TipoAccionAuditoria tipoAccion,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta,
            Pageable pageable);
}
