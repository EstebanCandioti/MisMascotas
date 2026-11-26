package com.MisMascotas.backend.Service;

import com.MisMascotas.backend.Audit.Auditable;
import com.MisMascotas.backend.DTO.RecordatorioRequestDTO;
import com.MisMascotas.backend.DTO.RecordatorioResponseDTO;
import com.MisMascotas.backend.Entity.Estado;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.OcurrenciaRecordatorio;
import com.MisMascotas.backend.Entity.Recordatorio;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.EstadoNoConfiguradoException;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Exception.ReglaNegocioException;
import com.MisMascotas.backend.Exception.ValidacionRequestException;
import com.MisMascotas.backend.Recordatorios.CalculadorOcurrencias;
import com.MisMascotas.backend.Recordatorios.ProgramacionRecordatorio;
import com.MisMascotas.backend.Repository.EstadoRepository;
import com.MisMascotas.backend.Repository.OcurrenciaRecordatorioRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class RecordatorioService {

    private static final String ENTIDAD_RECORDATORIO = "RECORDATORIO";
    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_INACTIVO = "INACTIVO";
    private static final String ESTADO_COMPLETADO = "COMPLETADO";
    private static final String MODALIDAD_UNICA = "unica";
    private static final ZoneId ZONA = CalculadorOcurrencias.ZONA;

    private final RecordatorioRepository recordatorioRepository;
    private final OcurrenciaRecordatorioRepository ocurrenciaRecordatorioRepository;
    private final EstadoRepository estadoRepository;
    private final AccesoMascotaService accesoMascotaService;
    private final EntityManager entityManager;
    private final Clock reloj;

    @Auditable(entidad = "recordatorio", accion = TipoAccionAuditoria.CREATE)
    @Transactional
    public RecordatorioResponseDTO crear(RecordatorioRequestDTO request, UUID creadoPorId) {
        Mascota mascota = accesoMascotaService.verificarGestionSalud(request.mascotaId(), creadoPorId);
        var plan = ProgramacionRecordatorio.desde(request);
        var fechas = calcularOcurrenciasDeAlta(plan);

        Usuario usuarioRef = entityManager.getReference(Usuario.class, creadoPorId);
        Estado estadoDefault = buscarEstadoRecordatorio(ESTADO_ACTIVO);

        Recordatorio recordatorio = new Recordatorio();
        recordatorio.setMascota(mascota);
        recordatorio.setCreadoPor(usuarioRef);
        recordatorio.setTitulo(request.titulo());
        recordatorio.setTipo(normalizarTipo(request.tipo()));
        guardarProgramacion(recordatorio, plan);
        recordatorio.setEstado(estadoDefault);
        recordatorio.setCreadoEn(reloj.instant());
        actualizarProgreso(recordatorio, plan, fechas);

        Recordatorio recordatorioGuardado = recordatorioRepository.save(recordatorio);
        guardarOcurrencias(recordatorioGuardado, fechas);

        return mapToResponse(recordatorioGuardado);
    }

    @Transactional
    public void renovarTanda(UUID id, LocalDate hoy) {
        var recordatorio = recordatorioRepository.findById(id).orElse(null);
        if (recordatorio == null) return;
        entityManager.refresh(recordatorio, LockModeType.PESSIMISTIC_WRITE);
        var ultimaFecha = recordatorio.getUltimaFechaGenerada();
        if (recordatorio.getFechaEliminacion() != null || recordatorio.getMascota().getFechaEliminacion() != null
                || !tieneEstado(recordatorio, ESTADO_ACTIVO) || esUnico(recordatorio)
                || recordatorio.getFechaFin() != null || ultimaFecha == null || ultimaFecha.isAfter(hoy)) {
            return;
        }

        // Si la tanda aún cubre hoy, continuar desde mañana para evitar duplicar esa ocurrencia.
        var fechaCorte = ultimaFecha.equals(hoy) ? hoy.plusDays(1) : hoy;
        var plan = ProgramacionRecordatorio.desde(recordatorio);
        var fechas = calcularOcurrenciasDesde(plan, fechaCorte.atStartOfDay(ZONA).toInstant());
        actualizarProgreso(recordatorio, plan, fechas);
        guardarOcurrencias(recordatorio, fechas);
    }

    private void guardarOcurrencias(Recordatorio recordatorio, List<Instant> fechas) {
        if (fechas.isEmpty()) return;
        var ocurrencias = fechas.stream().map(fecha -> nuevaOcurrencia(recordatorio, fecha)).toList();
        // Verificar las inserciones antes de devolver éxito y registrar la auditoría.
        ocurrenciaRecordatorioRepository.saveAllAndFlush(ocurrencias);
    }

    private List<Instant> calcularOcurrenciasDeAlta(ProgramacionRecordatorio plan) {
        var fechas = calcularOcurrenciasDesde(plan, plan.inicio());
        if (plan.esSerie() && fechas.isEmpty()) {
            throw new ValidacionRequestException("fechaFin: ninguna ocurrencia coincide antes del fin");
        }
        return fechas;
    }

    private List<Instant> calcularOcurrenciasDesde(ProgramacionRecordatorio plan, Instant desde) {
        if (!plan.esSerie()) return List.of();
        if (plan.fin() == null) {
            int cantidad = ProgramacionRecordatorio.OCURRENCIAS_POR_TANDA;
            var fechas = CalculadorOcurrencias.desde(plan, desde, cantidad);
            if (fechas.size() != cantidad) {
                throw new ValidacionRequestException("intervaloValor: no permite calcular una tanda completa");
            }
            return fechas;
        }
        int maximo = ProgramacionRecordatorio.MAX_OCURRENCIAS_CON_FIN;
        var fechas = CalculadorOcurrencias.desde(plan, desde, maximo + 1);
        if (fechas.size() > maximo) {
            throw new ValidacionRequestException("fechaFin: la serie supera " + maximo + " ocurrencias");
        }
        return fechas;
    }

    private void actualizarProgreso(Recordatorio recordatorio, ProgramacionRecordatorio plan, List<Instant> fechas) {
        recordatorio.setUltimaFechaGenerada(fechas.isEmpty() || plan.fin() != null
                ? null : fechas.getLast().atZone(ZONA).toLocalDate());
    }

    private OcurrenciaRecordatorio nuevaOcurrencia(Recordatorio recordatorio, Instant fecha) {
        var ocurrencia = new OcurrenciaRecordatorio();
        ocurrencia.setRecordatorio(recordatorio);
        ocurrencia.setFechaProgramada(fecha);
        return ocurrencia;
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
        var plan = ProgramacionRecordatorio.desde(request);
        if (plan.fin() != null) calcularOcurrenciasDeAlta(plan);
        bloquearRegistroActivo(recordatorio);

        recordatorio.setTitulo(request.titulo());
        recordatorio.setTipo(normalizarTipo(request.tipo()));
        if (cambioProgramacion(recordatorio, plan)) {
            aplicarProgramacionEditada(recordatorio, plan, hoy());
        }
        return mapToResponse(recordatorioRepository.saveAndFlush(recordatorio));
    }

    private boolean cambioProgramacion(Recordatorio recordatorio, ProgramacionRecordatorio plan) {
        try {
            return !plan.equals(ProgramacionRecordatorio.desde(recordatorio));
        } catch (ValidacionRequestException error) {
            // Una programación anterior inválida también se puede corregir con un PUT válido.
            return true;
        }
    }

    private void aplicarProgramacionEditada(Recordatorio recordatorio, ProgramacionRecordatorio plan, LocalDate hoy) {
        var manana = hoy.plusDays(1).atStartOfDay(ZONA).toInstant();
        boolean eraUnico = esUnico(recordatorio);
        if (!plan.esSerie()) {
            validarFechaUnicaEditada(recordatorio, plan, manana, eraUnico);
        } else if (eraUnico) {
            conservarFechaDelUnicoAnterior(recordatorio, hoy, manana);
        }

        guardarProgramacion(recordatorio, plan);
        if (!eraUnico || plan.esSerie()) {
            var fechas = tieneEstado(recordatorio, ESTADO_ACTIVO) ? calcularOcurrenciasDesde(plan, manana) : List.<Instant>of();
            actualizarProgreso(recordatorio, plan, fechas);
            ajustarOcurrenciasDesde(recordatorio, manana, fechas);
        }
    }

    private void validarFechaUnicaEditada(Recordatorio recordatorio, ProgramacionRecordatorio plan, Instant manana,
            boolean eraUnico) {
        if (!eraUnico && plan.inicio().isBefore(manana)) {
            throw new ValidacionRequestException("fechaHoraInicio: el cambio de serie a única debe programarse desde mañana");
        }
        var dia = plan.inicio().atZone(ZONA).toLocalDate();
        if (ocurrenciaRecordatorioRepository.hayOcurrenciaConservada(recordatorio.getIdRecordatorio(),
                dia.atStartOfDay(ZONA).toInstant(), dia.plusDays(1).atStartOfDay(ZONA).toInstant(), eraUnico)) {
            throw new ValidacionRequestException("fechaHoraInicio: esta fecha ya tiene una ocurrencia conservada");
        }
    }

    private void conservarFechaDelUnicoAnterior(Recordatorio recordatorio, LocalDate hoy, Instant manana) {
        var fechaAnterior = recordatorio.getFechaHoraInicio();
        boolean confirmado = recordatorio.getConfirmadoPor() != null && recordatorio.getConfirmadoEn() != null;
        boolean pendienteHoy = tieneEstado(recordatorio, ESTADO_INACTIVO)
                && !fechaAnterior.isBefore(hoy.atStartOfDay(ZONA).toInstant());
        if (confirmado || (fechaAnterior.isBefore(manana) && !pendienteHoy)) {
            var anterior = nuevaOcurrencia(recordatorio, fechaAnterior);
            if (confirmado) {
                anterior.setConfirmadoPor(recordatorio.getConfirmadoPor());
                anterior.setConfirmadoEn(recordatorio.getConfirmadoEn());
                recordatorio.setConfirmadoPor(null);
                recordatorio.setConfirmadoEn(null);
            }
            ocurrenciaRecordatorioRepository.saveAndFlush(anterior);
        }
        if (tieneEstado(recordatorio, ESTADO_COMPLETADO)) {
            recordatorio.setEstado(buscarEstadoRecordatorio(ESTADO_ACTIVO));
        }
    }

    private void ajustarOcurrenciasDesde(Recordatorio recordatorio, Instant desde, List<Instant> fechas) {
        var existentes = ocurrenciaRecordatorioRepository
                .findByRecordatorio_IdRecordatorioAndFechaProgramadaGreaterThanEqual(recordatorio.getIdRecordatorio(), desde);
        var fechasDeseadas = Set.copyOf(fechas);
        var diasConfirmados = existentes.stream().filter(o -> o.getConfirmadoEn() != null)
                .map(o -> o.getFechaProgramada().atZone(ZONA).toLocalDate()).collect(Collectors.toSet());
        var obsoletas = existentes.stream().filter(o -> o.getConfirmadoEn() == null)
                .filter(o -> !fechasDeseadas.contains(o.getFechaProgramada())
                        || diasConfirmados.contains(o.getFechaProgramada().atZone(ZONA).toLocalDate()))
                .toList();
        if (!obsoletas.isEmpty()) ocurrenciaRecordatorioRepository.deleteAllInBatch(obsoletas);

        var fechasExistentes = existentes.stream().map(OcurrenciaRecordatorio::getFechaProgramada)
                .collect(Collectors.toSet());
        var faltantes = fechas.stream().filter(fecha -> !fechasExistentes.contains(fecha))
                .filter(fecha -> !diasConfirmados.contains(fecha.atZone(ZONA).toLocalDate())).toList();
        guardarOcurrencias(recordatorio, faltantes);
    }

    private void guardarProgramacion(Recordatorio recordatorio, ProgramacionRecordatorio plan) {
        recordatorio.setFechaHoraInicio(plan.inicio());
        recordatorio.setModalidad(plan.modalidad().name().toLowerCase(Locale.ROOT));
        recordatorio.setIntervaloValor(plan.intervalo());
        recordatorio.setIntervaloUnidad(plan.unidad() == null ? null : plan.unidad().name().toLowerCase(Locale.ROOT));
        recordatorio.setDiaSemana(plan.diasParaGuardar());
        recordatorio.setFechaFin(plan.fin());
    }

    @Auditable(entidad = "recordatorio", accion = TipoAccionAuditoria.UPDATE)
    @Transactional
    public RecordatorioResponseDTO cambiarEstado(UUID id, String nuevoEstado, UUID usuarioAutenticadoId,
            UUID idOcurrencia) {
        Recordatorio recordatorio = buscarRecordatorioActivo(id);
        accesoMascotaService.verificarGestionSalud(recordatorio.getMascota(), usuarioAutenticadoId);
        bloquearRegistroActivo(recordatorio);

        if (ESTADO_COMPLETADO.equals(normalizarEstado(nuevoEstado))) {
            return confirmarRegistro(recordatorio, usuarioAutenticadoId, idOcurrencia);
        }
        if (idOcurrencia != null) {
            throw new ValidacionRequestException("idOcurrencia: solo corresponde al cambiar el estado a COMPLETADO");
        }

        Estado estado = buscarEstadoRecordatorio(nuevoEstado);
        if (!esUnico(recordatorio)) {
            gestionarCambioDeEstadoDeSerie(recordatorio, estado, hoy());
        }
        recordatorio.setEstado(estado);

        return mapToResponse(recordatorioRepository.saveAndFlush(recordatorio));
    }

    private void gestionarCambioDeEstadoDeSerie(Recordatorio recordatorio, Estado estado, LocalDate hoy) {
        var desde = hoy.atStartOfDay(ZONA).toInstant();
        if (ESTADO_INACTIVO.equals(estado.getNombre()) && !tieneEstado(recordatorio, ESTADO_INACTIVO)) {
            recordatorio.setUltimaFechaGenerada(null);
            ajustarOcurrenciasDesde(recordatorio, desde, List.of());
        } else if (ESTADO_ACTIVO.equals(estado.getNombre()) && !tieneEstado(recordatorio, ESTADO_ACTIVO)) {
            var plan = ProgramacionRecordatorio.desde(recordatorio);
            var fechas = calcularOcurrenciasDesde(plan, desde);
            if (fechas.isEmpty()) {
                throw new ReglaNegocioException("La serie no tiene más ocurrencias dentro de su fecha de fin");
            }
            actualizarProgreso(recordatorio, plan, fechas);
            ajustarOcurrenciasDesde(recordatorio, desde, fechas);
        }
    }

    @Auditable(entidad = "recordatorio", accion = TipoAccionAuditoria.CONFIRM)
    @Transactional
    public RecordatorioResponseDTO confirmar(UUID id, UUID usuarioAutenticadoId, UUID idOcurrencia) {
        Recordatorio recordatorio = buscarRecordatorioActivo(id);
        accesoMascotaService.verificarLectura(recordatorio.getMascota(), usuarioAutenticadoId);
        bloquearRegistroActivo(recordatorio);
        return confirmarRegistro(recordatorio, usuarioAutenticadoId, idOcurrencia);
    }

    private RecordatorioResponseDTO confirmarRegistro(Recordatorio recordatorio, UUID usuarioAutenticadoId,
            UUID idOcurrencia) {
        var ocurrencia = resolverOcurrencia(recordatorio, idOcurrencia);
        Instant ahora = reloj.instant();
        var diaActual = ahora.atZone(ZONA).toLocalDate();
        if (!tieneEstado(recordatorio, ESTADO_ACTIVO) || estaConfirmado(recordatorio, ocurrencia)) {
            throw new ReglaNegocioException("El recordatorio ya esta confirmado o no esta activo");
        }
        Instant fecha = ocurrencia == null ? recordatorio.getFechaHoraInicio() : ocurrencia.getFechaProgramada();
        if (!fecha.atZone(ZONA).toLocalDate().equals(diaActual)) {
            throw new ReglaNegocioException("Solo se puede confirmar durante el dia correspondiente al recordatorio");
        }

        Usuario usuarioRef = entityManager.getReference(Usuario.class, usuarioAutenticadoId);
        if (ocurrencia != null) {
            ocurrencia.setConfirmadoPor(usuarioRef);
            ocurrencia.setConfirmadoEn(ahora);
            ocurrenciaRecordatorioRepository.saveAndFlush(ocurrencia);
            return mapToResponse(recordatorio, ocurrencia, diaActual);
        }
        recordatorio.setConfirmadoPor(usuarioRef);
        recordatorio.setConfirmadoEn(ahora);
        recordatorio.setEstado(buscarEstadoRecordatorio(ESTADO_COMPLETADO));

        Recordatorio recordatorioActualizado = recordatorioRepository.save(recordatorio);

        return mapToResponse(recordatorioActualizado);
    }

    @Auditable(entidad = "recordatorio", accion = TipoAccionAuditoria.UPDATE)
    @Transactional
    public RecordatorioResponseDTO desmarcarConfirmacion(UUID id, UUID usuarioAutenticadoId, UUID idOcurrencia) {
        Recordatorio recordatorio = buscarRecordatorioActivo(id);
        accesoMascotaService.verificarGestionSalud(recordatorio.getMascota(), usuarioAutenticadoId);
        bloquearRegistroActivo(recordatorio);

        var ocurrencia = resolverOcurrencia(recordatorio, idOcurrencia);
        if (ocurrencia != null) {
            ocurrencia.setConfirmadoPor(null);
            ocurrencia.setConfirmadoEn(null);
            ocurrenciaRecordatorioRepository.saveAndFlush(ocurrencia);
            return mapToResponse(recordatorio, ocurrencia, hoy());
        }

        recordatorio.setConfirmadoPor(null);
        recordatorio.setConfirmadoEn(null);
        if (tieneEstado(recordatorio, ESTADO_COMPLETADO)) {
            recordatorio.setEstado(buscarEstadoRecordatorio(ESTADO_ACTIVO));
        }

        Recordatorio recordatorioActualizado = recordatorioRepository.save(recordatorio);

        return mapToResponse(recordatorioActualizado);
    }

    private OcurrenciaRecordatorio resolverOcurrencia(Recordatorio recordatorio, UUID idOcurrencia) {
        if (idOcurrencia == null) {
            if (esUnico(recordatorio)) return null;
            throw new ValidacionRequestException("idOcurrencia: es obligatorio para una serie de recordatorios");
        }
        return ocurrenciaRecordatorioRepository
                .findByIdOcurrenciaAndRecordatorio_IdRecordatorio(idOcurrencia, recordatorio.getIdRecordatorio())
                .orElseThrow(() -> {
                    if (esUnico(recordatorio)) {
                        return new ValidacionRequestException("idOcurrencia: no corresponde a este recordatorio único");
                    }
                    return new RecursoNoEncontradoException("Ocurrencia de recordatorio no encontrada");
                });
    }

    @Auditable(entidad = "recordatorio", accion = TipoAccionAuditoria.DELETE)
    @Transactional
    public void eliminar(UUID id, UUID usuarioAutenticadoId) {
        Recordatorio recordatorio = buscarRecordatorioActivo(id);
        accesoMascotaService.verificarGestionSalud(recordatorio.getMascota(), usuarioAutenticadoId);
        bloquearRegistroActivo(recordatorio);

        recordatorio.setFechaEliminacion(reloj.instant());
        recordatorioRepository.save(recordatorio);
    }

    @Transactional(readOnly = true)
    public List<RecordatorioResponseDTO> listarHoy(UUID actorId) {
        var ids = accesoMascotaService.obtenerMascotasAccesibles(actorId).stream().map(Mascota::getIdMascota).toList();
        if (ids.isEmpty()) return List.of();
        var hoy = hoy();
        var desde = hoy.atStartOfDay(ZONA).toInstant();
        var hasta = hoy.plusDays(1).atStartOfDay(ZONA).toInstant();
        var unicos = recordatorioRepository.buscarUnicosDelDia(ids, desde, hasta).stream()
                .map(recordatorio -> mapToResponse(recordatorio, null, hoy));
        var ocurrencias = ocurrenciaRecordatorioRepository.buscarDelDia(ids, desde, hasta).stream()
                .map(ocurrencia -> mapToResponse(ocurrencia.getRecordatorio(), ocurrencia, hoy));
        return Stream.concat(unicos, ocurrencias)
                .sorted(Comparator.comparing((RecordatorioResponseDTO respuesta) -> respuesta.fechaProgramada() != null
                        ? respuesta.fechaProgramada() : respuesta.fechaHoraInicio())
                        .thenComparing(respuesta -> respuesta.idRecordatorio().toString()))
                .toList();
    }

    private LocalDate hoy() { return reloj.instant().atZone(ZONA).toLocalDate(); }

    private boolean estaConfirmado(Recordatorio r, OcurrenciaRecordatorio ocurrencia) {
        return ocurrencia != null ? ocurrencia.getConfirmadoEn() != null
                : r.getConfirmadoEn() != null || r.getConfirmadoPor() != null || tieneEstado(r, ESTADO_COMPLETADO);
    }

    private String situacionTemporal(Recordatorio r, OcurrenciaRecordatorio ocurrencia, LocalDate hoy) {
        if (tieneEstado(r, ESTADO_INACTIVO)) return ESTADO_INACTIVO;
        if (estaConfirmado(r, ocurrencia)) return "COMPLETADO";
        var fecha = ocurrencia != null ? ocurrencia.getFechaProgramada() : r.getFechaHoraInicio();
        int comparacion = fecha.atZone(ZONA).toLocalDate().compareTo(hoy);
        return comparacion < 0 ? "VENCIDO" : comparacion > 0 ? "PROXIMO" : "PENDIENTE";
    }

    private Recordatorio buscarRecordatorioActivo(UUID id) {
        return recordatorioRepository.findByIdRecordatorioAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Recordatorio no encontrado"));
    }

    private void bloquearRegistroActivo(Recordatorio recordatorio) {
        // El padre coordina cambios de estado, ejecuciones del job y confirmaciones de una misma serie.
        entityManager.refresh(recordatorio, LockModeType.PESSIMISTIC_WRITE);
        if (recordatorio.getFechaEliminacion() != null) {
            throw new RecursoNoEncontradoException("Recordatorio no encontrado");
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

    private boolean esUnico(Recordatorio recordatorio) {
        return recordatorio.getModalidad() != null
                && MODALIDAD_UNICA.equals(recordatorio.getModalidad().trim().toLowerCase(Locale.ROOT));
    }

    private boolean tieneEstado(Recordatorio recordatorio, String nombreEstado) {
        return recordatorio.getEstado() != null
                && nombreEstado.equals(normalizarEstado(recordatorio.getEstado().getNombre()));
    }

    private RecordatorioResponseDTO mapToResponse(Recordatorio recordatorio) {
        return mapToResponse(recordatorio, null, hoy());
    }

    private RecordatorioResponseDTO mapToResponse(Recordatorio recordatorio, OcurrenciaRecordatorio ocurrencia,
            LocalDate hoy) {
        String situacion = situacionTemporal(recordatorio, ocurrencia, hoy);
        var confirmadoPor = ocurrencia != null ? ocurrencia.getConfirmadoPor() : recordatorio.getConfirmadoPor();
        var confirmadoEn = ocurrencia != null ? ocurrencia.getConfirmadoEn() : recordatorio.getConfirmadoEn();
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
                confirmadoPor != null ? confirmadoPor.getIdUsuario() : null,
                confirmadoEn,
                recordatorio.getEstado() != null ? recordatorio.getEstado().getNombre() : null,
                situacion,
                (ocurrencia != null || esUnico(recordatorio))
                        && tieneEstado(recordatorio, ESTADO_ACTIVO) && "PENDIENTE".equals(situacion),
                ocurrencia != null ? ocurrencia.getIdOcurrencia() : null,
                ocurrencia != null ? ocurrencia.getFechaProgramada() : null
        );
    }
}
