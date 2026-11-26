package com.MisMascotas.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.MisMascotas.backend.Entity.Recordatorio;

@Repository
public interface RecordatorioRepository extends JpaRepository<Recordatorio, UUID> {
    @Query(value = """
        SELECT r.id_recordatorio AS "idRecordatorio", r.ultima_fecha_generada AS "ultimaFechaGenerada"
        FROM public.recordatorio r
        JOIN public.estados e ON e.id_estado = r.estado_id
        JOIN public.mascota m ON m.id_mascota = r.mascota_id
        WHERE r.modalidad IN ('recurrente', 'dias_semana')
          AND r.fecha_fin IS NULL AND r.fecha_eliminacion IS NULL
          AND r.ultima_fecha_generada IS NOT NULL AND r.ultima_fecha_generada <= :hoy
          AND m.fecha_eliminacion IS NULL AND e.entidad = 'RECORDATORIO' AND e.nombre = 'ACTIVO'
          AND (cast(:fechaCursor as date) IS NULL
               OR (r.ultima_fecha_generada, r.id_recordatorio) > (:fechaCursor, :idCursor))
        ORDER BY r.ultima_fecha_generada, r.id_recordatorio
        LIMIT :limite
        """, nativeQuery = true)
    List<SerieParaRenovar> buscarSeriesParaRenovar(@Param("hoy") LocalDate hoy,
            @Param("fechaCursor") LocalDate fechaCursor, @Param("idCursor") UUID idCursor, @Param("limite") int limite);

    interface SerieParaRenovar {
        UUID getIdRecordatorio();
        LocalDate getUltimaFechaGenerada();
    }

    @Query("""
        select r from Recordatorio r where r.mascota.idMascota in :mascotas
          and r.fechaEliminacion is null and r.mascota.fechaEliminacion is null
          and r.modalidad = 'unica'
          and r.fechaHoraInicio >= :desde and r.fechaHoraInicio < :hasta
          and r.estado.entidad = 'RECORDATORIO' and r.estado.nombre <> 'INACTIVO'
        order by r.fechaHoraInicio, r.idRecordatorio
        """)
    List<Recordatorio> buscarUnicosDelDia(@Param("mascotas") List<UUID> mascotas,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    List<Recordatorio> findByMascota_IdMascotaAndFechaEliminacionIsNull(UUID mascotaId);

    Optional<Recordatorio> findByIdRecordatorioAndFechaEliminacionIsNull(UUID idRecordatorio);
}
