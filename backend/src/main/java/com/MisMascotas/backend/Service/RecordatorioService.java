package com.MisMascotas.backend.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.DTO.RecordatorioRequestDTO;
import com.MisMascotas.backend.DTO.RecordatorioResponseDTO;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.Recordatorio;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.RecordatorioRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecordatorioService {

    private final RecordatorioRepository recordatorioRepository;
    private final EntityManager entityManager;

    @Transactional
    public RecordatorioResponseDTO crear(RecordatorioRequestDTO request, UUID creadoPorId) {
        Mascota mascotaRef = entityManager.getReference(Mascota.class, request.mascotaId());
        Usuario usuarioRef = entityManager.getReference(Usuario.class, creadoPorId);

        Recordatorio recordatorio = new Recordatorio();
        recordatorio.setMascota(mascotaRef);
        recordatorio.setCreadoPor(usuarioRef);
        recordatorio.setTitulo(request.titulo());
        recordatorio.setTipo(request.tipo());
        recordatorio.setFechaHoraInicio(request.fechaHoraInicio());
        recordatorio.setModalidad(request.modalidad());
        recordatorio.setIntervaloValor(request.intervaloValor());
        recordatorio.setIntervaloUnidad(request.intervaloUnidad());
        recordatorio.setDiaSemana(request.diasSemana());
        recordatorio.setFechaFin(request.fechaFin());

        Recordatorio guardado = recordatorioRepository.save(recordatorio);
        return mapToResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<RecordatorioResponseDTO> listarPorMascota(UUID mascotaId) {
        return recordatorioRepository.findByMascotaIdMascotaAndFechaEliminacionIsNull(mascotaId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecordatorioResponseDTO obtenerPorId(UUID id) {
        Recordatorio recordatorio = recordatorioRepository.findByIdRecordatorioAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Recordatorio no encontrado con ID: " + id));
        return mapToResponse(recordatorio);
    }

    @Transactional
    public RecordatorioResponseDTO editar(UUID id, RecordatorioRequestDTO request) {
        Recordatorio recordatorio = recordatorioRepository.findByIdRecordatorioAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Recordatorio no encontrado con ID: " + id));

        recordatorio.setTitulo(request.titulo());
        recordatorio.setTipo(request.tipo());
        recordatorio.setFechaHoraInicio(request.fechaHoraInicio());
        recordatorio.setModalidad(request.modalidad());
        recordatorio.setIntervaloValor(request.intervaloValor());
        recordatorio.setIntervaloUnidad(request.intervaloUnidad());
        recordatorio.setDiaSemana(request.diasSemana());
        recordatorio.setFechaFin(request.fechaFin());

        Recordatorio actualizado = recordatorioRepository.save(recordatorio);
        return mapToResponse(actualizado);
    }

    @Transactional
    public RecordatorioResponseDTO cambiarEstado(UUID id, String nuevoEstado) {
        Recordatorio recordatorio = recordatorioRepository.findByIdRecordatorioAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Recordatorio no encontrado con ID: " + id));

        Recordatorio actualizado = recordatorioRepository.save(recordatorio);
        return mapToResponse(actualizado);
    }

    @Transactional
    public void eliminar(UUID id) {
        Recordatorio recordatorio = recordatorioRepository.findByIdRecordatorioAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Recordatorio no encontrado con ID: " + id));

        recordatorio.setFechaEliminacion(LocalDateTime.now());
        recordatorioRepository.save(recordatorio);
    }

    private RecordatorioResponseDTO mapToResponse(Recordatorio recordatorio) {
        return new RecordatorioResponseDTO(
                recordatorio.getIdRecordatorio(),
                recordatorio.getMascota() != null ? recordatorio.getMascota().getIdMascota() : null,
                recordatorio.getTitulo(),
                recordatorio.getTipo(),
                recordatorio.getFechaHoraInicio(),
                recordatorio.getModalidad(),
                recordatorio.getIntervaloValor(),
                recordatorio.getIntervaloUnidad(),
                recordatorio.getDiaSemana(),
                recordatorio.getFechaFin(),
                recordatorio.getEstado() != null ? recordatorio.getEstado().getNombre() : "ACTIVO",
                recordatorio.getCreadoPor() != null ? recordatorio.getCreadoPor().getIdUsuario() : null,
                recordatorio.getConfirmadoPor() != null ? recordatorio.getConfirmadoPor().getIdUsuario() : null,
                recordatorio.getConfirmadoEn()
        );
    }
}
