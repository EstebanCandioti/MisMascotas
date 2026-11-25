package com.MisMascotas.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.MisMascotas.backend.Entity.Recordatorio;

@Repository
public interface RecordatorioRepository extends JpaRepository<Recordatorio, UUID> {
    @Query("""
        select r from Recordatorio r where r.mascota.idMascota in :mascotas
          and r.fechaEliminacion is null and r.mascota.fechaEliminacion is null
          and r.fechaHoraInicio >= :desde and r.fechaHoraInicio < :hasta
          and r.estado.entidad = 'RECORDATORIO' and r.estado.nombre <> 'INACTIVO'
        order by r.fechaHoraInicio, r.idRecordatorio
        """)
    List<Recordatorio> buscarDelDia(@Param("mascotas") List<UUID> mascotas,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    List<Recordatorio> findByMascota_IdMascotaAndFechaEliminacionIsNull(UUID mascotaId);

    Optional<Recordatorio> findByIdRecordatorioAndFechaEliminacionIsNull(UUID idRecordatorio);
}
