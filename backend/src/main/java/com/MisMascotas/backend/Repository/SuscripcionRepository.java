package com.MisMascotas.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.MisMascotas.backend.Entity.Suscripcion;

import jakarta.persistence.LockModeType;

@Repository
public interface SuscripcionRepository extends JpaRepository<Suscripcion, UUID> {

    Optional<Suscripcion> findFirstByUsuario_IdUsuarioAndTipoPlanAndEstadoCatalogo_IdEstadoOrderByFechaInicioDesc(
            UUID usuarioId,
            String tipoPlan,
            Integer estadoId);

    Optional<Suscripcion> findByIdSuscripcionAndUsuario_IdUsuario(
            UUID idSuscripcion,
            UUID usuarioId);

    @Query("select s from Suscripcion s join fetch s.usuario where s.idSuscripcionMercadopago = :idMercadoPago")
    Optional<Suscripcion> findByIdSuscripcionMercadopagoConUsuario(@Param("idMercadoPago") String idMercadoPago);

    Optional<Suscripcion>
            findFirstByUsuario_IdUsuarioAndTipoPlanAndEstadoCatalogo_IdEstadoAndIdSuscripcionNotOrderByFechaInicioDesc(
                    UUID usuarioId,
                    String tipoPlan,
                    Integer estadoId,
                    UUID idSuscripcionExcluida);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Suscripcion s where s.idSuscripcion = :suscripcionId")
    Optional<Suscripcion> findByIdConBloqueo(@Param("suscripcionId") UUID suscripcionId);

    long countByUsuario_IdUsuarioAndTipoPlanAndEstadoCatalogo_IdEstado(
            UUID usuarioId,
            String tipoPlan,
            Integer estadoId);

    List<Suscripcion> findAllByUsuario_IdUsuario(UUID usuarioId);

    @Query(value = """
            SELECT DISTINCT s.usuario_id FROM public.suscripcion s
            JOIN public.usuario u ON u.id_usuario = s.usuario_id
            WHERE s.fecha_vencimiento <= :ahora
              AND EXISTS (SELECT 1 FROM public.pago p JOIN public.estados ep ON ep.id_estado = p.estado_id
                          WHERE p.suscripcion_id = s.id_suscripcion AND ep.entidad = 'PAGO' AND ep.nombre = 'APROBADO')
              AND (s.estado_id IN (SELECT id_estado FROM public.estados WHERE entidad = 'SUSCRIPCION' AND nombre = 'ACTIVA')
                   OR (u.es_premium AND NOT EXISTS (
                       SELECT 1 FROM public.suscripcion otra WHERE otra.usuario_id = s.usuario_id
                       AND otra.fecha_vencimiento > :ahora
                       AND EXISTS (SELECT 1 FROM public.pago p2 JOIN public.estados ep2 ON ep2.id_estado = p2.estado_id
                                   WHERE p2.suscripcion_id = otra.id_suscripcion AND ep2.entidad = 'PAGO' AND ep2.nombre = 'APROBADO'))))
            ORDER BY s.usuario_id LIMIT :limite
            """, nativeQuery = true)
    List<UUID> buscarUsuariosConCoberturaVencida(@Param("ahora") java.time.Instant ahora, @Param("limite") int limite);
}
