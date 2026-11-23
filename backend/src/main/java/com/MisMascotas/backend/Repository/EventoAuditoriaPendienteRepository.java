package com.MisMascotas.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.MisMascotas.backend.Entity.EventoAuditoriaPendiente;

public interface EventoAuditoriaPendienteRepository extends JpaRepository<EventoAuditoriaPendiente, UUID> {

    List<EventoAuditoriaPendiente> findByIdEntidadOrderByFechaEvento(UUID idEntidad);

    @Query("""
            SELECT evento.idEvento
            FROM EventoAuditoriaPendiente evento
            WHERE evento.procesadoEn IS NULL
            ORDER BY evento.fechaEvento, evento.idEvento
            """)
    List<UUID> buscarIdsPendientes(Pageable pageable);

    @Query(value = """
            SELECT *
            FROM evento_auditoria_pendiente
            WHERE id_evento = :idEvento
              AND procesado_en IS NULL
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<EventoAuditoriaPendiente> buscarPendienteParaProcesar(@Param("idEvento") UUID idEvento);
}
