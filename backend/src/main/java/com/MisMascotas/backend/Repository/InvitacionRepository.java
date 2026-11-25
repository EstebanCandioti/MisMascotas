package com.MisMascotas.backend.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.MisMascotas.backend.Entity.Invitacion;

public interface InvitacionRepository extends JpaRepository<Invitacion, UUID> {
    List<Invitacion> findByPropietario_IdUsuarioOrderByCreadoEnDesc(UUID propietarioId);

    // El llamador normaliza el email con el mismo criterio que autenticación.
    List<Invitacion> findByEmailInvitadoOrderByCreadoEnDesc(String email);

    Optional<Invitacion> findByIdInvitacionAndPropietario_IdUsuario(UUID invitacionId, UUID propietarioId);

    Optional<Invitacion> findByIdInvitacionAndEmailInvitado(UUID invitacionId, String email);

    @Query("""
        select count(i) from Invitacion i where i.propietario.idUsuario = :propietarioId
          and i.estado.entidad = 'INVITACION' and i.estado.nombre = 'PENDIENTE' and i.expiraEn > :ahora
        """)
    long contarPendientesVigentes(@Param("propietarioId") UUID propietarioId, @Param("ahora") Instant ahora);

    @Query("""
        select (count(i) > 0) from Invitacion i where i.propietario.idUsuario = :propietarioId
          and i.emailInvitado = :email and i.estado.entidad = 'INVITACION'
          and i.estado.nombre = 'PENDIENTE' and i.expiraEn > :ahora
        """)
    boolean existePendienteVigente(@Param("propietarioId") UUID propietarioId,
            @Param("email") String email, @Param("ahora") Instant ahora);

    // Siempre después del bloqueo del propietario; evita invertir el orden de los escritores.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitacion i where i.idInvitacion = :id")
    Optional<Invitacion> findByIdConBloqueo(@Param("id") UUID id);
}
