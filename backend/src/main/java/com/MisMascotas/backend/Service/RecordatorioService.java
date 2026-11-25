package com.MisMascotas.backend.Service;

import com.MisMascotas.backend.Audit.Auditable;
import com.MisMascotas.backend.DTO.RecordatorioRequestDTO;
import com.MisMascotas.backend.DTO.RecordatorioResponseDTO;
import com.MisMascotas.backend.Entity.Estado;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.Recordatorio;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.EstadoNoConfiguradoException;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Exception.ReglaNegocioException;
import com.MisMascotas.backend.Repository.EstadoRepository;
import com.MisMascotas.backend.Repository.RecordatorioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecordatorioService {

    private static final String ENTIDAD_RECORDATORIO = "RECORDATORIO";
    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_COMPLETADO = "COMPLETADO";
    private static final String MODALIDAD_UNICA = "unica";
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final RecordatorioRepository recordatorioRepository;
    private final EstadoRepository estadoRepository;
    private final AccesoMascotaService accesoMascotaService;
    private final EntityManager entityManager;
    private final Clock reloj;

    @Auditable(entidad = "recordatorio", accion = TipoAccionAuditoria.CREATE)
    @Transactional
    public RecordatorioResponseDTO crear(RecordatorioRequestDTO request, UUID creadoPorId) {
        Mascota mascota = accesoMascotaService.verificarGestionSalud(request.mascotaId(), creadoPorId);

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
        recordatorio.setCreadoEn(reloj.instant());

        Recordatorio recordatorioGuardado = recordatorioRepository.save(recordatorio);

        return mapToResponse(recordatorioGuardado);
    }

    @Transactional(readOnly = true)
    public List<RecordatorioResponseDTO> listarPorMascota(UUID mascotaId, UUID usuarioAutenticadoId) {
        accesoMascotaService.verificarLectura(mascotaId, usuarioAutenticadoId);

        List<Recordatorio> recordatorios = recordatorioRepository.findByMascota_IdMascotaAndFechaEliminacionIsNull(mascotaId);
        return recordatorios.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecordatorioResponseDTO obtenerPorId(UUID id, UUID usuarioAutenticadoId) {
        Recordatorio recordatorio = buscarRecordatorioActivo(id);
        accesoMascotaService.verificarLectura(recordatorio.getMascota(), usuarioAutenticadoId);
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
        accesoMascotaService.verificarGestionSalud(recordatorio.getMascota(), usuarioAutenticadoId);

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
        accesoMascotaService.verificarGestionSalud(recordatorio.getMascota(), usuarioAutenticadoId);

        if (ESTADO_COMPLETADO.equals(normalizarEstado(nuevoEstado))) {
            return confirmarRegistro(recordatorio, usuarioAutenticadoId);
        }

        Estado estado = buscarEstadoRecordatorio(nuevoEstado);

        recordatorio.setEstado(estado);

        Recordatorio recordatorioActualizado = recordatorioRepository.save(recordatorio);

        return mapToResponse(recordatorioActualizado);
    }

    @Auditable(entidad = "recordatorio", accion = TipoAccionAuditoria.CONFIRM)
    @Transactional
    public RecordatorioResponseDTO confirmar(UUID id, UUID usuarioAutenticadoId) {
        Recordatorio recordatorio = buscarRecordatorioActivo(id);
        accesoMascotaService.verificarLectura(recordatorio.getMascota(), usuarioAutenticadoId);
        return confirmarRegistro(recordatorio, usuarioAutenticadoId);
    }

    private RecordatorioResponseDTO confirmarRegistro(Recordatorio recordatorio, UUID usuarioAutenticadoId) {
        // Refrescar bajo bloqueo evita sobrescribir la confirmación de otra petición.
        entityManager.refresh(recordatorio, LockModeType.PESSIMISTIC_WRITE);
        Instant ahora = reloj.instant();
        if (recordatorio.getFechaEliminacion() != null) {
            throw new RecursoNoEncontradoException("Recordatorio no encontrado");
        }
        if (!tieneEstado(recordatorio, ESTADO_ACTIVO) || estaConfirmado(recordatorio)) {
            throw new ReglaNegocioException("El recordatorio ya esta confirmado o no esta activo");
        }
        if (!recordatorio.getFechaHoraInicio().atZone(ZONA).toLocalDate().equals(ahora.atZone(ZONA).toLocalDate())) {
            throw new ReglaNegocioException("Solo se puede confirmar durante el dia correspondiente al recordatorio");
        }

        Usuario usuarioRef = entityManager.getReference(Usuario.class, usuarioAutenticadoId);
        recordatorio.setConfirmadoPor(usuarioRef);
        recordatorio.setConfirmadoEn(ahora);
        if (esUnico(recordatorio) && tieneEstado(recordatorio, ESTADO_ACTIVO)) {
            recordatorio.setEstado(buscarEstadoRecordatorio(ESTADO_COMPLETADO));
        }

        Recordatorio recordatorioActualizado = recordatorioRepository.save(recordatorio);

        return mapToResponse(recordatorioActualizado);
    }

    @Auditable(entidad = "recordatorio", accion = TipoAccionAuditoria.UPDATE)
    @Transactional
    public RecordatorioResponseDTO desmarcarConfirmacion(UUID id, UUID usuarioAutenticadoId) {
        Recordatorio recordatorio = buscarRecordatorioActivo(id);
        accesoMascotaService.verificarGestionSalud(recordatorio.getMascota(), usuarioAutenticadoId);

        recordatorio.setConfirmadoPor(null);
        recordatorio.setConfirmadoEn(null);
        if (esUnico(recordatorio) && tieneEstado(recordatorio, ESTADO_COMPLETADO)) {
            recordatorio.setEstado(buscarEstadoRecordatorio(ESTADO_ACTIVO));
        }

        Recordatorio recordatorioActualizado = recordatorioRepository.save(recordatorio);

        return mapToResponse(recordatorioActualizado);
    }
    @Auditable(entidad = "recordatorio", accion = TipoAccionAuditoria.DELETE)
    @Transactional
    public void eliminar(UUID id, UUID usuarioAutenticadoId) {
        Recordatorio recordatorio = buscarRecordatorioActivo(id);
        accesoMascotaService.verificarGestionSalud(recordatorio.getMascota(), usuarioAutenticadoId);

        recordatorio.setFechaEliminacion(reloj.instant());
        recordatorioRepository.save(recordatorio);
    }

    @Transactional(readOnly = true)
    public List<RecordatorioResponseDTO> listarHoy(UUID actorId) {
        var ids = accesoMascotaService.obtenerMascotasAccesibles(actorId).stream().map(Mascota::getIdMascota).toList();
        if (ids.isEmpty()) return List.of();
        var hoy = hoy();
        return recordatorioRepository.buscarDelDia(ids, hoy.atStartOfDay(ZONA).toInstant(), hoy.plusDays(1).atStartOfDay(ZONA).toInstant())
                .stream().map(this::mapToResponse).toList();
    }

    private LocalDate hoy() { return reloj.instant().atZone(ZONA).toLocalDate(); }

    private boolean estaConfirmado(Recordatorio r) {
        return r.getConfirmadoEn() != null || r.getConfirmadoPor() != null || tieneEstado(r, ESTADO_COMPLETADO);
    }

    private String situacionTemporal(Recordatorio r) {
        if (tieneEstado(r, "INACTIVO")) return "INACTIVO";
        if (estaConfirmado(r)) return "COMPLETADO";
        int comparacion = r.getFechaHoraInicio().atZone(ZONA).toLocalDate().compareTo(hoy());
        return comparacion < 0 ? "VENCIDO" : comparacion > 0 ? "PROXIMO" : "PENDIENTE";
    }

    private Recordatorio buscarRecordatorioActivo(UUID id) {
        return recordatorioRepository.findByIdRecordatorioAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Recordatorio no encontrado"));
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

    private boolean esUnico(Recordatorio recordatorio) {
        return recordatorio.getModalidad() != null
                && MODALIDAD_UNICA.equals(recordatorio.getModalidad().trim().toLowerCase(Locale.ROOT));
    }

    private boolean tieneEstado(Recordatorio recordatorio, String nombreEstado) {
        return recordatorio.getEstado() != null
                && nombreEstado.equals(normalizarEstado(recordatorio.getEstado().getNombre()));
    }

    private RecordatorioResponseDTO mapToResponse(Recordatorio recordatorio) {
        String situacion = situacionTemporal(recordatorio);
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
                recordatorio.getEstado() != null ? recordatorio.getEstado().getNombre() : null,
                situacion,
                tieneEstado(recordatorio, ESTADO_ACTIVO) && "PENDIENTE".equals(situacion)
        );
    }
}
