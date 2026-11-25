package com.MisMascotas.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.MisMascotas.backend.Entity.AccesoCompartido;

public interface AccesoCompartidoRepository extends JpaRepository<AccesoCompartido, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccesoCompartido a where a.idAcceso = :id")
    Optional<AccesoCompartido> findByIdConBloqueo(@Param("id") UUID id);
    long countByPropietario_IdUsuarioAndActivoTrue(UUID propietarioId);

    Optional<AccesoCompartido> findByPropietario_IdUsuarioAndInvitado_IdUsuarioAndActivoTrue(
            UUID propietarioId, UUID invitadoId);

    List<AccesoCompartido> findByPropietario_IdUsuarioAndActivoTrueOrderByFechaIngresoDesc(UUID propietarioId);

    List<AccesoCompartido> findByInvitado_IdUsuarioAndActivoTrueOrderByFechaIngresoDesc(UUID invitadoId);

    Optional<AccesoCompartido> findByIdAccesoAndPropietario_IdUsuario(UUID accesoId, UUID propietarioId);

    Optional<AccesoCompartido> findByIdAccesoAndInvitado_IdUsuario(UUID accesoId, UUID invitadoId);
}
