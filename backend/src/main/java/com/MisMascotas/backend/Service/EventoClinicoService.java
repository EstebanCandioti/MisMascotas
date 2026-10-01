package com.MisMascotas.backend.Service;

import java.time.Instant;
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
        validarFechaNoFutura(request.fecha());

        Mascota mascota = buscarMascotaActiva(request.mascotaId());
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
        buscarMascotaActiva(mascotaId);

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
        EventoClinico evento = buscarEventoActivo(id);
        return mapToResponse(evento);
    }

    @Transactional
    public EventoClinicoResponseDTO editar(UUID id, EventoClinicoRequestDTO request) {
        validarFechaNoFutura(request.fecha());

        EventoClinico evento = buscarEventoActivo(id);
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
        EventoClinico evento = buscarEventoActivo(id);
        evento.setFechaEliminacion(Instant.now());
        eventoClinicoRepository.save(evento);
    }

    private Mascota buscarMascotaActiva(UUID id) {
        return mascotaRepository.findByIdMascotaAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("La mascota no existe o fue eliminada."));
    }

    private EventoClinico buscarEventoActivo(UUID id) {
        return eventoClinicoRepository.findByIdEventoAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El evento clinico no existe."));
    }

    private void validarFechaNoFutura(LocalDate fecha) {
        if (fecha != null && fecha.isAfter(LocalDate.now())) {
            throw new ReglaNegocioException("La fecha no puede ser posterior al dia actual.");
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
                null,
                evento.getCreadoEn()
        );
    }
}