package com.MisMascotas.backend.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.Audit.Auditable;
import com.MisMascotas.backend.DTO.BadgeRequestDTO;
import com.MisMascotas.backend.DTO.BadgeResponseDTO;
import com.MisMascotas.backend.Entity.Badge;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Exception.LimiteBadgesAlcanzadoException;
import com.MisMascotas.backend.Repository.BadgeRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BadgeService {

    private static final int LIMITE_BADGES_ACTIVAS = 3;

    private final BadgeRepository badgeRepository;
    private final AccesoMascotaService accesoMascotaService;
    private final UsuarioRepository usuarioRepository;
    private final PremiumService premiumService;
    private final EntityManager entityManager;

    @Auditable(entidad = "badge", accion = TipoAccionAuditoria.CREATE)
    @Transactional
    public BadgeResponseDTO crear(BadgeRequestDTO request, UUID usuarioAutenticadoId) {
        Mascota mascota = bloquearMascotaPropia(request.mascotaId(), usuarioAutenticadoId);
        premiumService.exigirPremium(mascota.getPropietario().getIdUsuario());
        if (badgeRepository.countByMascotaIdMascotaAndFechaEliminacionIsNull(mascota.getIdMascota())
                >= LIMITE_BADGES_ACTIVAS) {
            throw new LimiteBadgesAlcanzadoException();
        }

        Badge badge = new Badge();
        badge.setMascota(mascota);
        badge.setTexto(request.texto());
        badge.setEmoji(request.emoji());

        Badge guardado = badgeRepository.saveAndFlush(badge);
        return mapToResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<BadgeResponseDTO> listarPorMascota(UUID mascotaId, UUID usuarioAutenticadoId) {
        Mascota mascota = accesoMascotaService.verificarLectura(mascotaId, usuarioAutenticadoId);

        return badgeRepository.findByMascotaIdMascotaAndFechaEliminacionIsNull(mascotaId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BadgeResponseDTO obtenerPorId(UUID id, UUID usuarioAutenticadoId) {
        Badge badge = buscarBadgeActiva(id);
        accesoMascotaService.verificarLectura(badge.getMascota(), usuarioAutenticadoId);
        return mapToResponse(badge);
    }

    /**
     * Uso exclusivo del aspecto de auditoria para capturar el estado anterior.
     * Los controllers deben usar la variante que recibe usuarioAutenticadoId.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public BadgeResponseDTO obtenerPorId(UUID id) {
        return mapToResponse(buscarBadgeActiva(id));
    }

    @Auditable(entidad = "badge", accion = TipoAccionAuditoria.DELETE)
    @Transactional
    public void eliminar(UUID id, UUID usuarioAutenticadoId) {
        Badge badge = buscarBadgeActiva(id);
        bloquearMascotaPropia(badge.getMascota().getIdMascota(), usuarioAutenticadoId);
        // Otra eliminación puede haber confirmado mientras esperábamos el bloqueo.
        badge = buscarBadgeActiva(id);
        entityManager.refresh(badge);

        badge.setFechaEliminacion(Instant.now());
        badgeRepository.save(badge);
    }

    private Mascota bloquearMascotaPropia(UUID mascotaId, UUID actorId) {
        Mascota mascota = accesoMascotaService.verificarAcceso(mascotaId, actorId);
        var propietario = usuarioRepository.findByIdConBloqueo(mascota.getPropietario().getIdUsuario())
                .orElseThrow(() -> new RecursoNoEncontradoException("Propietario no encontrado"));
        // Mismo orden que los escritores de suscripción y cuidado compartido.
        entityManager.refresh(propietario, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(mascota, LockModeType.PESSIMISTIC_WRITE);
        accesoMascotaService.verificarAcceso(mascota, actorId);
        return mascota;
    }

    private Badge buscarBadgeActiva(UUID id) {
        return badgeRepository.findByIdBadgeAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Badge no encontrada con ID: " + id));
    }


    private BadgeResponseDTO mapToResponse(Badge badge) {
        return new BadgeResponseDTO(
                badge.getIdBadge(),
                badge.getMascota() != null ? badge.getMascota().getIdMascota() : null,
                badge.getTexto(),
                badge.getEmoji()
        );
    }
}
