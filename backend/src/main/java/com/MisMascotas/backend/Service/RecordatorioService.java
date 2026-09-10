package com.MisMascotas.backend.Service;

import com.MisMascotas.backend.DTO.RecordatorioRequestDTO;
import com.MisMascotas.backend.DTO.RecordatorioResponseDTO;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.EstadoRepository;
import com.MisMascotas.backend.Repository.RecordatorioRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecordatorioService {

    private final RecordatorioRepository recordatorioRepository;
    private final EstadoRepository estadoRepository;
    private final EntityManager entityManager;

    @Transactional
    public RecordatorioResponseDTO crear(RecordatorioRequestDTO request, UUID creadoPorId) {
        Usuario usuarioRef = entityManager.getReference(Usuario.class, creadoPorId);
        Mascota mascotaRef = entityManager.getReference(Mascota.class, request.mascotaId());
        
        // Obtener el estado por defecto (activo o pendiente)
        Estado estadoDefault = estadoRepository.findByNombreIgnoreCase("activo")
                .orElseThrow(() -> new RecursoNoEncontradoException("Estado no encontrado"));

        Recordatorio recordatorio = new Recordatorio();
        recordatorio.setMascota(mascotaRef);
        recordatorio.setCreadoPor(usuarioRef);
        recordatorio.setTitulo(request.titulo());
        recordatorio.setTipo(request.tipo());
        recordatorio.setFechaHoraInicio(request.fechaHoraInicio());
        recordatorio.setModalidad(request.modalidad() != null ? request.modalidad() : "unica");
        recordatorio.setIntervaloValor(request.intervaloValor());
        recordatorio.setIntervaloUnidad(request.intervaloUnidad());
        recordatorio.setDiaSemana(request.diasSemana());
        recordatorio.setFechaFin(request.fechaFin());
        recordatorio.setEstado(estadoDefault);
        recordatorio.setCreadoEn(Instant.now());

        Recordatorio recordatorioGuardado = recordatorioRepository.save(recordatorio);

        return mapToResponse(recordatorioGuardado);
    }

    @Transactional(readOnly = true)
    public List<RecordatorioResponseDTO> listarPorMascota(UUID mascotaId) {
        List<Recordatorio> recordatorios = recordatorioRepository.findByMascota_IdMascota(mascotaId);
        return recordatorios.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RecordatorioResponseDTO obtenerPorId(UUID id) {
        Recordatorio recordatorio = recordatorioRepository.findByIdRecordatorio(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Recordatorio no encontrado"));
        return mapToResponse(recordatorio);
    }

    @Transactional
    public RecordatorioResponseDTO editar(UUID id, RecordatorioRequestDTO request) {
        Recordatorio recordatorio = recordatorioRepository.findByIdRecordatorio(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Recordatorio no encontrado"));

        recordatorio.setTitulo(request.titulo());
        recordatorio.setTipo(request.tipo());
        recordatorio.setFechaHoraInicio(request.fechaHoraInicio());
        recordatorio.setModalidad(request.modalidad() != null ? request.modalidad() : "unica");
        recordatorio.setIntervaloValor(request.intervaloValor());
        recordatorio.setIntervaloUnidad(request.intervaloUnidad());
        recordatorio.setDiaSemana(request.diasSemana());
        recordatorio.setFechaFin(request.fechaFin());

        Recordatorio recordatorioActualizado = recordatorioRepository.save(recordatorio);

        return mapToResponse(recordatorioActualizado);
    }

    @Transactional
    public RecordatorioResponseDTO cambiarEstado(UUID id, String nuevoEstado) {
        Recordatorio recordatorio = recordatorioRepository.findByIdRecordatorio(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Recordatorio no encontrado"));

        Estado estado = estadoRepository.findByNombreIgnoreCase(nuevoEstado)
                .orElseThrow(() -> new RecursoNoEncontradoException("Estado no encontrado"));

        recordatorio.setEstado(estado);

        Recordatorio recordatorioActualizado = recordatorioRepository.save(recordatorio);

        return mapToResponse(recordatorioActualizado);
    }

    @Transactional
    public void eliminar(UUID id) {
        Recordatorio recordatorio = recordatorioRepository.findByIdRecordatorio(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Recordatorio no encontrado"));

        recordatorioRepository.delete(recordatorio);
    }

    private RecordatorioResponseDTO mapToResponse(Recordatorio recordatorio) {
        return new RecordatorioResponseDTO(
                recordatorio.getIdRecordatorio(),
                recordatorio.getMascota().getIdMascota(),
                recordatorio.getTitulo(),
                recordatorio.getTipo(),
                recordatorio.getFechaHoraInicio(),
                recordatorio.getModalidad(),
                recordatorio.getIntervaloValor(),
                recordatorio.getIntervaloUnidad(),
                recordatorio.getDiaSemana(),
                recordatorio.getFechaFin(),
                recordatorio.getCreadoPor().getIdUsuario(),
                recordatorio.getCreadoEn(),
                recordatorio.getEstado() != null ? recordatorio.getEstado().getNombre() : null
        );
    }
}
