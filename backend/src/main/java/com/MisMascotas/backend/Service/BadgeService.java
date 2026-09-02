package com.MisMascotas.backend.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.DTO.BadgeRequestDTO;
import com.MisMascotas.backend.DTO.BadgeResponseDTO;
import com.MisMascotas.backend.Entity.Badge;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.BadgeRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BadgeService {

    private final BadgeRepository badgeRepository;
    private final EntityManager entityManager;

    @Transactional
    public BadgeResponseDTO crear(BadgeRequestDTO request) {
        Mascota mascotaRef = entityManager.getReference(Mascota.class, request.mascotaId());

        Badge badge = new Badge();
        badge.setMascota(mascotaRef);
        badge.setTexto(request.texto());
        badge.setEmoji(request.emoji());

        Badge guardado = badgeRepository.save(badge);
        return mapToResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<BadgeResponseDTO> listarPorMascota(UUID mascotaId) {
        return badgeRepository.findByMascotaIdMascotaAndFechaEliminacionIsNull(mascotaId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BadgeResponseDTO obtenerPorId(UUID id) {
        Badge badge = badgeRepository.findByIdBadgeAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Badge no encontrada con ID: " + id));
        return mapToResponse(badge);
    }

    @Transactional
    public void eliminar(UUID id) {
        Badge badge = badgeRepository.findByIdBadgeAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Badge no encontrada con ID: " + id));

        badge.setFechaEliminacion(LocalDateTime.now());
        badgeRepository.save(badge);
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
