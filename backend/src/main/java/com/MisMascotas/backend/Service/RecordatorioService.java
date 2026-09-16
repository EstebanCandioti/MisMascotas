package com.MisMascotas.backend.Service;

import com.MisMascotas.backend.Audit.Auditable;
import com.MisMascotas.backend.DTO.RecordatorioRequestDTO;
import com.MisMascotas.backend.DTO.RecordatorioResponseDTO;
import com.MisMascotas.backend.Entity.Estado;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.Recordatorio;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.AccesoDenegadoException;
import com.MisMascotas.backend.Exception.EstadoNoConfiguradoException;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.EstadoRepository;
import com.MisMascotas.backend.Repository.MascotaRepository;
import com.MisMascotas.backend.Repository.RecordatorioRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecordatorioService {

    private static final String ENTIDAD_RECORDATORIO = "RECORDATORIO";
    private static final String ESTADO_ACTIVO = "ACTIVO";

    private final RecordatorioRepository recordatorioRepository;
    private final EstadoRepository estadoRepository;
    private final MascotaRepository mascotaRepository;
    private final EntityManager entityManager;

    @Auditable(entidad = "recordatorio", accion = TipoAccionAuditoria.CREATE)
    @Transactional
    public RecordatorioResponseDTO crear(RecordatorioRequestDTO request, UUID creadoPorId) {
        Mascota mascota = buscarMascotaActiva(request.mascotaId());
        validarAccesoMascota(mascota, creadoPorId);

        Usuario usuarioRef = entityManager.getReference(Usuario.class, creadoPorId);
        Estado estadoDefault = buscarEstadoRecordatorio(ESTADO_ACTIVO);

        Recordatorio recordatorio = new Recordatorio();
        recordatorio.setMascota(mascota);
        recordatorio.setCreadoPor(usuarioRef);
        recordatorio.setTitulo(request.titulo());
        recordatorio.setTipo(normalizarTipo(request.tipo()));
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
    public List<RecordatorioResponseDTO> listarPorMascota(UUID mascotaId, UUID usuarioAutenticadoId) {
        Mascota mascota = buscarMascotaActiva(mascotaId);
        validarAccesoMascota(mascota, usuarioAutenticadoId);

        List<Recordatorio> recordatorios = recordatorioRepository.findByMascota_IdMascotaAndFechaEliminacionIsNull(mascotaId);
        return recordatorios.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecordatorioResponseDTO obtenerPorId(UUID id, UUID usuarioAutenticadoId) {
        Recordatorio recordatorio = buscarRecordatorioActivo(id);
        validarAccesoRecordatorio(recordatorio, usuarioAutenticadoId);
        return mapToResponse(recordatorio);
    }

    @Transactional(readOnly = true)
    public RecordatorioResponseDTO obtenerPorId(UUID id) {
        Recordatorio recordatorio = buscarRecordatorioActivo(id);
        return mapToResponse(recordatorio);
    }

    @Auditable(entidad = "recordatorio", accion = TipoAccionAuditoria.UPDATE)
    @Transactional
    public RecordatorioResponseDTO editar(UUID id, RecordatorioRequestDTO request, UUID usuarioAutenticadoId) {
        Recordatorio recordatorio = buscarRecordatorioActivo(id);
        validarAccesoRecordatorio(recordatorio, usuarioAutenticadoId);

        recordatorio.setTitulo(request.titulo());
        recordatorio.setTipo(normalizarTipo(request.tipo()));
        recordatorio.setFechaHoraInicio(request.fechaHoraInicio());
        recordatorio.setModalidad(request.modalidad() != null ? request.modalidad() : "unica");
        recordatorio.setIntervaloValor(request.intervaloValor());
        recordatorio.setIntervaloUnidad(request.intervaloUnidad());
        recordatorio.setDiaSemana(request.diasSemana());
        recordatorio.setFechaFin(request.fechaFin());

        Recordatorio recordatorioActualizado = recordatorioRepository.save(recordatorio);

        return mapToResponse(recordatorioActualizado);
    }

    @Auditable(entidad = "recordatorio", accion = TipoAccionAuditoria.UPDATE)
    @Transactional
    public RecordatorioResponseDTO cambiarEstado(UUID id, String nuevoEstado, UUID usuarioAutenticadoId) {
        Recordatorio recordatorio = buscarRecordatorioActivo(id);
        validarAccesoRecordatorio(recordatorio, usuarioAutenticadoId);

        Estado estado = buscarEstadoRecordatorio(nuevoEstado);

        recordatorio.setEstado(estado);

        Recordatorio recordatorioActualizado = recordatorioRepository.save(recordatorio);

        return mapToResponse(recordatorioActualizado);
    }

    @Auditable(entidad = "recordatorio", accion = TipoAccionAuditoria.CONFIRM)
    @Transactional
    public RecordatorioResponseDTO confirmar(UUID id, UUID usuarioAutenticadoId) {
        Recordatorio recordatorio = buscarRecordatorioActivo(id);
        validarAccesoRecordatorio(recordatorio, usuarioAutenticadoId);

        Usuario usuarioRef = entityManager.getReference(Usuario.class, usuarioAutenticadoId);
        recordatorio.setConfirmadoPor(usuarioRef);
        recordatorio.setConfirmadoEn(Instant.now());

        Recordatorio recordatorioActualizado = recordatorioRepository.save(recordatorio);

        return mapToResponse(recordatorioActualizado);
    }

    @Auditable(entidad = "recordatorio", accion = TipoAccionAuditoria.UPDATE)
    @Transactional
    public RecordatorioResponseDTO desmarcarConfirmacion(UUID id, UUID usuarioAutenticadoId) {
        Recordatorio recordatorio = buscarRecordatorioActivo(id);
        validarAccesoRecordatorio(recordatorio, usuarioAutenticadoId);

        recordatorio.setConfirmadoPor(null);
        recordatorio.setConfirmadoEn(null);

        Recordatorio recordatorioActualizado = recordatorioRepository.save(recordatorio);

        return mapToResponse(recordatorioActualizado);
    }
    @Auditable(entidad = "recordatorio", accion = TipoAccionAuditoria.DELETE)
    @Transactional
    public void eliminar(UUID id, UUID usuarioAutenticadoId) {
        Recordatorio recordatorio = buscarRecordatorioActivo(id);
        validarAccesoRecordatorio(recordatorio, usuarioAutenticadoId);

        recordatorio.setFechaEliminacion(Instant.now());
        recordatorioRepository.save(recordatorio);
    }

    private Recordatorio buscarRecordatorioActivo(UUID id) {
        return recordatorioRepository.findByIdRecordatorioAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Recordatorio no encontrado"));
    }

    private Mascota buscarMascotaActiva(UUID id) {
        return mascotaRepository.findByIdMascotaAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada con ID: " + id));
    }

    private void validarAccesoRecordatorio(Recordatorio recordatorio, UUID usuarioAutenticadoId) {
        validarAccesoMascota(recordatorio.getMascota(), usuarioAutenticadoId);
    }

    private void validarAccesoMascota(Mascota mascota, UUID usuarioAutenticadoId) {
        UUID propietarioId = mascota.getPropietario() != null ? mascota.getPropietario().getIdUsuario() : null;
        if (!usuarioAutenticadoId.equals(propietarioId)) {
            throw new AccesoDenegadoException("No tenes permisos para realizar esta accion sobre esta mascota");
        }
    }

    private Estado buscarEstadoRecordatorio(String nombre) {
        String nombreNormalizado = normalizarEstado(nombre);
        return estadoRepository.findByEntidadAndNombre(ENTIDAD_RECORDATORIO, nombreNormalizado)
                .orElseThrow(() -> new EstadoNoConfiguradoException(ENTIDAD_RECORDATORIO, nombreNormalizado));
    }

    private String normalizarEstado(String nombre) {
        return nombre == null ? "" : nombre.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizarTipo(String tipo) {
        return tipo == null ? null : tipo.trim().toUpperCase(Locale.ROOT);
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
                recordatorio.getConfirmadoPor() != null ? recordatorio.getConfirmadoPor().getIdUsuario() : null,
                recordatorio.getConfirmadoEn(),
                recordatorio.getEstado() != null ? recordatorio.getEstado().getNombre() : null
        );
    }
}