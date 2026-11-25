package com.MisMascotas.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.MisMascotas.backend.Entity.Mascota;

@Repository
public interface MascotaRepository extends JpaRepository<Mascota, UUID> {
    @Query("""
        select m from Mascota m where m.fechaEliminacion is null and
          (m.propietario.idUsuario = :usuarioId or exists
            (select a from AccesoCompartido a where a.activo = true
             and a.propietario.idUsuario = m.propietario.idUsuario and a.invitado.idUsuario = :usuarioId
             and a.propietario.activo = true and a.invitado.activo = true))
        order by m.creadoEn desc, m.idMascota
        """)
    List<Mascota> findAccesibles(@Param("usuarioId") UUID usuarioId);

    List<Mascota> findByPropietario_IdUsuarioAndFechaEliminacionIsNull(UUID propietarioId);

    Optional<Mascota> findByIdMascotaAndFechaEliminacionIsNull(UUID idMascota);

    long countByPropietario_IdUsuarioAndFechaEliminacionIsNull(UUID propietarioId);
}
