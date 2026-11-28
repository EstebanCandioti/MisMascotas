package com.MisMascotas.backend.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.MisMascotas.backend.Entity.OcurrenciaRecordatorio;

public interface OcurrenciaRecordatorioRepository extends JpaRepository<OcurrenciaRecordatorio, UUID> {
    @Query("""
        select o from OcurrenciaRecordatorio o
        join fetch o.recordatorio r
        join fetch r.estado e
        join fetch r.mascota m
        where m.idMascota in :mascotas
          and r.fechaEliminacion is null and m.fechaEliminacion is null
          and e.entidad = 'RECORDATORIO' and e.nombre = 'ACTIVO'
          and o.fechaProgramada >= :desde and o.fechaProgramada < :hasta
        """)
    List<OcurrenciaRecordatorio> buscarEntreFechas(@Param("mascotas") List<UUID> mascotas,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    Optional<OcurrenciaRecordatorio> findByIdOcurrenciaAndRecordatorio_IdRecordatorio(
            UUID idOcurrencia, UUID idRecordatorio);

    List<OcurrenciaRecordatorio> findByRecordatorio_IdRecordatorioAndFechaProgramadaGreaterThanEqual(
            UUID idRecordatorio, Instant desde);

    @Query("""
        select count(o) > 0 from OcurrenciaRecordatorio o
        where o.recordatorio.idRecordatorio = :id and o.fechaProgramada >= :desde and o.fechaProgramada < :hasta
          and (:conservarPendientes = true or o.confirmadoEn is not null)
        """)
    boolean hayOcurrenciaConservada(@Param("id") UUID id, @Param("desde") Instant desde,
            @Param("hasta") Instant hasta, @Param("conservarPendientes") boolean conservarPendientes);
}
