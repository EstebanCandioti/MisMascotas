<<<<<<< Updated upstream
=======
package com.MisMascotas.backend.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.DTO.EventoClinicoRequestDTO;
import com.MisMascotas.backend.DTO.EventoClinicoResponseDTO;
import com.MisMascotas.backend.Entity.EventoClinico;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Exception.ReglaNegocioException;
import com.MisMascotas.backend.Repository.EventoClinicoRepository;
import com.MisMascotas.backend.Repository.MascotaRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventoClinicoService {

    private final EventoClinicoRepository eventoClinicoRepository;
    private final MascotaRepository mascotaRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public EventoClinicoResponseDTO registrar(EventoClinicoRequestDTO request, UUID registradoPorId) {
        // RN-025: La fecha del evento no puede ser posterior al día actual
        validarFechaNoFutura(request.fecha());

        // Verificar existencia de la mascota y que no esté eliminada
        Mascota mascota = mascotaRepository.findByIdMascotaAndFechaEliminacionIsNull(request.mascotaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La mascota no existe o fue eliminada."));

        Usuario usuario = usuarioRepository.findById(registradoPorId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con ID: " + registradoPorId));

        EventoClinico evento = new EventoClinico();
        evento.setMascota(mascota);
        evento.setRegistradoPor(usuario);
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
        // Verificar existencia de la mascota
        if (!mascotaRepository.existsByIdMascotaAndFechaEliminacionIsNull(mascotaId)) {
            throw new RecursoNoEncontradoException("La mascota no existe o fue eliminada.");
        }

        List<EventoClinico> eventos;
        if (tipo != null && !tipo.isBlank()) {
            eventos = eventoClinicoRepository.findByMascotaIdAndTipoOrderByFechaDesc(mascotaId, tipo);
        } else {
            eventos = eventoClinicoRepository.findByMascotaIdOrderByFechaDesc(mascotaId);
        }

        return eventos.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public EventoClinicoResponseDTO obtenerPorId(UUID id) {
        EventoClinico evento = eventoClinicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El evento clínico no existe."));
        return mapToResponse(evento);
    }

    @Transactional
    public EventoClinicoResponseDTO editar(UUID id, EventoClinicoRequestDTO request) {
        // RN-025: La fecha del evento no puede ser posterior al día actual
        validarFechaNoFutura(request.fecha());

        EventoClinico evento = eventoClinicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El evento clínico no existe."));

        // Se actualizan los campos (se ignora mascotaId en la edición)
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
        EventoClinico evento = eventoClinicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El evento clínico no existe."));

        // Hard Delete según la especificación técnica
        eventoClinicoRepository.delete(evento);
    }

    private void validarFechaNoFutura(LocalDate fecha) {
        if (fecha != null && fecha.isAfter(LocalDate.now())) {
            throw new ReglaNegocioException("La fecha no puede ser posterior al día actual.");
        }
    }

    private EventoClinicoResponseDTO mapToResponse(EventoClinico evento) {
        return new EventoClinicoResponseDTO(
                evento.getIdEvento(),
                evento.getMascota() != null ? evento.getMascota().getIdMascota() : null,
                evento.getRegistradoPor() != null ? evento.getRegistradoPor().getIdUsuario() : null,
                evento.getRegistradoPor() != null ? evento.getRegistradoPor().getNombre() : null,
                evento.getTipo(),
                evento.getFecha(),
                evento.getNombre(),
                evento.getDosis(),
                evento.getValorPeso(),
                evento.getMotivo(),
                evento.getDiagnostico(),
                evento.getObservaciones(),
                null, // urlAdjunto libre por ahora
                evento.getCreadoEn()
        );
    }
}
>>>>>>> Stashed changes
