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
import com.MisMascotas.backend.Exception.AccesoDenegadoException;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.BadgeRepository;
import com.MisMascotas.backend.Repository.MascotaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BadgeService {

    private final BadgeRepository badgeRepository;
    private final MascotaRepository mascotaRepository;

    @Auditable(entidad = "badge", accion = TipoAccionAuditoria.CREATE)
    @Transactional
    public BadgeResponseDTO crear(BadgeRequestDTO request, UUID usuarioAutenticadoId) {
        Mascota mascota = buscarMascotaActiva(request.mascotaId());
        validarAccesoMascota(mascota, usuarioAutenticadoId);

        Badge badge = new Badge();
        badge.setMascota(mascota);
        badge.setTexto(request.texto());
        badge.setEmoji(request.emoji());

        Badge guardado = badgeRepository.save(badge);
        return mapToResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<BadgeResponseDTO> listarPorMascota(UUID mascotaId, UUID usuarioAutenticadoId) {
        Mascota mascota = buscarMascotaActiva(mascotaId);
        validarAccesoMascota(mascota, usuarioAutenticadoId);

        return badgeRepository.findByMascotaIdMascotaAndFechaEliminacionIsNull(mascotaId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BadgeResponseDTO obtenerPorId(UUID id, UUID usuarioAutenticadoId) {
        Badge badge = buscarBadgeActiva(id);
        validarAccesoMascota(badge.getMascota(), usuarioAutenticadoId);
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
        validarAccesoMascota(badge.getMascota(), usuarioAutenticadoId);

        badge.setFechaEliminacion(Instant.now());
        badgeRepository.save(badge);
    }

    private Badge buscarBadgeActiva(UUID id) {
        return badgeRepository.findByIdBadgeAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Badge no encontrada con ID: " + id));
    }

    private Mascota buscarMascotaActiva(UUID id) {
        return mascotaRepository.findByIdMascotaAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada con ID: " + id));
    }

    private void validarAccesoMascota(Mascota mascota, UUID usuarioAutenticadoId) {
        UUID propietarioId = mascota != null && mascota.getPropietario() != null ? mascota.getPropietario().getIdUsuario() : null;
        if (!usuarioAutenticadoId.equals(propietarioId)) {
            throw new AccesoDenegadoException("No tenes permisos para realizar esta accion sobre esta mascota");
        }
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