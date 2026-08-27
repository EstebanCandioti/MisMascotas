package com.MisMascotas.backend.Service;

import com.MisMascotas.backend.DTO.EventoClinicoRequestDTO;
import com.MisMascotas.backend.DTO.EventoClinicoResponseDTO;
import com.MisMascotas.backend.Entity.EventoClinico;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.EventoClinicoRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventoClinicoService {

    private final EventoClinicoRepository eventoClinicoRepository;
    private final EntityManager entityManager;

    @Transactional
    public EventoClinicoResponseDTO crear(EventoClinicoRequestDTO request, UUID registradoPorId) {
        Mascota mascotaRef = entityManager.getReference(Mascota.class, request.mascotaId());
        Usuario usuarioRef = entityManager.getReference(Usuario.class, registradoPorId);

        EventoClinico evento = new EventoClinico();
        evento.setMascota(mascotaRef);
        evento.setRegistradoPor(usuarioRef);
        evento.setTipo(request.tipo());
        evento.setFecha(request.fecha());
        evento.setNombre(request.nombre());
        evento.setDosis(request.dosis());
        evento.setValorPeso(request.valorNumerico());
        evento.setMotivo(request.motivo());
        evento.setDiagnostico(request.diagnostico());
        evento.setObservaciones(request.observaciones());

        EventoClinico guardado = eventoClinicoRepository.save(evento);
        return mapToResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<EventoClinicoResponseDTO> listarPorMascota(UUID mascotaId, String tipo) {
        List<EventoClinico> eventos;
        if (tipo != null && !tipo.isBlank()) {
            eventos = eventoClinicoRepository.findByMascotaIdMascotaAndTipoAndFechaEliminacionIsNullOrderByFechaDesc(mascotaId, tipo);
        } else {
            eventos = eventoClinicoRepository.findByMascotaIdMascotaAndFechaEliminacionIsNullOrderByFechaDesc(mascotaId);
        }
        return eventos.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public EventoClinicoResponseDTO obtenerPorId(UUID id) {
        EventoClinico evento = eventoClinicoRepository.findByIdEventoAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Evento clínico no encontrado con ID: " + id));
        return mapToResponse(evento);
    }

    @Transactional
    public EventoClinicoResponseDTO editar(UUID id, EventoClinicoRequestDTO request) {
        EventoClinico evento = eventoClinicoRepository.findByIdEventoAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Evento clínico no encontrado con ID: " + id));

        evento.setTipo(request.tipo());
        evento.setFecha(request.fecha());
        evento.setNombre(request.nombre());
        evento.setDosis(request.dosis());
        evento.setValorPeso(request.valorNumerico());
        evento.setMotivo(request.motivo());
        evento.setDiagnostico(request.diagnostico());
        evento.setObservaciones(request.observaciones());

        EventoClinico actualizado = eventoClinicoRepository.save(evento);
        return mapToResponse(actualizado);
    }

    @Transactional
    public void eliminar(UUID id) {
        EventoClinico evento = eventoClinicoRepository.findByIdEventoAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Evento clínico no encontrado con ID: " + id));

        evento.setFechaEliminacion(LocalDateTime.now());
        eventoClinicoRepository.save(evento);
    }

    private EventoClinicoResponseDTO mapToResponse(EventoClinico evento) {
        return new EventoClinicoResponseDTO(
                evento.getIdEvento(),
                evento.getMascota() != null ? evento.getMascota().getIdMascota() : null,
                evento.getRegistradoPor() != null ? evento.getRegistradoPor().getIdUsuario() : null,
                evento.getTipo(),
                evento.getFecha(),
                evento.getNombre(),
                evento.getDosis(),
                evento.getValorPeso(),
                evento.getMotivo(),
                evento.getDiagnostico(),
                evento.getObservaciones(),
                null
        );
    }
}
