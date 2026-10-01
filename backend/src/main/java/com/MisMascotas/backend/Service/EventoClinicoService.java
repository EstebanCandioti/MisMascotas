package com.MisMascotas.backend.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.Audit.Auditable;
import com.MisMascotas.backend.DTO.EventoClinicoRequestDTO;
import com.MisMascotas.backend.DTO.EventoClinicoResponseDTO;
import com.MisMascotas.backend.Entity.EventoClinico;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.AccesoDenegadoException;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Exception.ReglaNegocioException;
import com.MisMascotas.backend.Exception.ValidacionRequestException;
import com.MisMascotas.backend.Repository.EventoClinicoRepository;
import com.MisMascotas.backend.Repository.MascotaRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventoClinicoService {

    private static final String TIPO_VACUNA = "VACUNA";
    private static final String TIPO_MEDICACION = "MEDICACION";
    private static final String TIPO_CONSULTA_VETERINARIA = "CONSULTA_VETERINARIA";
    private static final String TIPO_PESO = "PESO";
    private static final BigDecimal PESO_MINIMO_KG = new BigDecimal("0.01");
    private static final BigDecimal PESO_MAXIMO_KG = new BigDecimal("300.00");
    private static final ZoneId ZONA_ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final EventoClinicoRepository eventoClinicoRepository;
    private final MascotaRepository mascotaRepository;
    private final UsuarioRepository usuarioRepository;

    @Auditable(entidad = "evento_clinico", accion = TipoAccionAuditoria.CREATE)
    @Transactional
    public EventoClinicoResponseDTO registrar(EventoClinicoRequestDTO request, UUID registradoPorId) {
        String tipoNormalizado = validarRequest(request);
        validarFechaNoFutura(request.fecha());

        Mascota mascota = buscarMascotaActiva(request.mascotaId());
        validarAccesoMascota(mascota, registradoPorId);

        Usuario usuario = usuarioRepository.findById(registradoPorId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con ID: " + registradoPorId));

        EventoClinico evento = new EventoClinico();
        evento.setMascota(mascota);
        evento.setRegistradoPor(usuario);
        aplicarDatos(evento, request, tipoNormalizado);

        EventoClinico guardado = eventoClinicoRepository.save(evento);
        recalcularPesoActual(mascota);
        return mapToResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<EventoClinicoResponseDTO> listarPorMascota(UUID mascotaId, String tipo, UUID usuarioAutenticadoId) {
        Mascota mascota = buscarMascotaActiva(mascotaId);
        validarAccesoMascota(mascota, usuarioAutenticadoId);

        List<EventoClinico> eventos;
        if (tipo != null && !tipo.isBlank()) {
            eventos = eventoClinicoRepository.findByMascotaIdAndTipoOrderByFechaDesc(mascotaId, normalizarTipo(tipo));
        } else {
            eventos = eventoClinicoRepository.findByMascotaIdOrderByFechaDesc(mascotaId);
        }

        return eventos.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public EventoClinicoResponseDTO obtenerPorId(UUID id, UUID usuarioAutenticadoId) {
        EventoClinico evento = buscarEventoActivo(id);
        validarAccesoEvento(evento, usuarioAutenticadoId);
        return mapToResponse(evento);
    }

    /**
     * Uso exclusivo del aspecto de auditoria para capturar el estado anterior.
     * Los controllers deben usar la variante que recibe usuarioAutenticadoId.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public EventoClinicoResponseDTO obtenerPorId(UUID id) {
        return mapToResponse(buscarEventoActivo(id));
    }

    @Auditable(entidad = "evento_clinico", accion = TipoAccionAuditoria.UPDATE)
    @Transactional
    public EventoClinicoResponseDTO editar(UUID id, EventoClinicoRequestDTO request, UUID usuarioAutenticadoId) {
        String tipoNormalizado = validarRequest(request);
        validarFechaNoFutura(request.fecha());

        EventoClinico evento = buscarEventoActivo(id);
        validarAccesoEvento(evento, usuarioAutenticadoId);

        Mascota mascota = evento.getMascota();
        aplicarDatos(evento, request, tipoNormalizado);

        EventoClinico actualizado = eventoClinicoRepository.save(evento);
        recalcularPesoActual(mascota);
        return mapToResponse(actualizado);
    }

    @Auditable(entidad = "evento_clinico", accion = TipoAccionAuditoria.DELETE)
    @Transactional
    public void eliminar(UUID id, UUID usuarioAutenticadoId) {
        EventoClinico evento = buscarEventoActivo(id);
        validarAccesoEvento(evento, usuarioAutenticadoId);

        Mascota mascota = evento.getMascota();
        evento.setFechaEliminacion(Instant.now());
        eventoClinicoRepository.save(evento);
        recalcularPesoActual(mascota);
    }

    private void aplicarDatos(EventoClinico evento, EventoClinicoRequestDTO request, String tipoNormalizado) {
        evento.setTipo(tipoNormalizado);
        evento.setFecha(request.fecha());
        evento.setNombre(request.nombre());
        evento.setDosis(request.dosis());
        evento.setValorPeso(request.valorPeso());
        evento.setMotivo(request.motivo());
        evento.setDiagnostico(request.diagnostico());
        evento.setObservaciones(request.observaciones());
    }

    private String validarRequest(EventoClinicoRequestDTO request) {
        String tipoNormalizado = normalizarTipo(request.tipo());
        List<String> errores = new ArrayList<>();

        if (!esTipoValido(tipoNormalizado)) {
            errores.add("tipo: El tipo de evento no es valido");
        }

        if (TIPO_VACUNA.equals(tipoNormalizado) && estaEnBlanco(request.nombre())) {
            errores.add("nombre: El nombre es obligatorio para vacunas");
        }

        if (TIPO_MEDICACION.equals(tipoNormalizado)) {
            if (estaEnBlanco(request.nombre())) {
                errores.add("nombre: El nombre es obligatorio para medicaciones");
            }
            if (estaEnBlanco(request.dosis())) {
                errores.add("dosis: La dosis es obligatoria para medicaciones");
            }
        }

        if (TIPO_CONSULTA_VETERINARIA.equals(tipoNormalizado) && estaEnBlanco(request.motivo())) {
            errores.add("motivo: El motivo es obligatorio para consultas veterinarias");
        }

        if (TIPO_PESO.equals(tipoNormalizado)) {
            if (request.valorPeso() == null) {
                errores.add("valorPeso: El valor de peso es obligatorio");
            } else if (request.valorPeso().compareTo(PESO_MINIMO_KG) < 0
                    || request.valorPeso().compareTo(PESO_MAXIMO_KG) > 0) {
                errores.add("valorPeso: El valor de peso debe estar entre 0.01 y 300.00 kg");
            }
        }

        if (!errores.isEmpty()) {
            throw new ValidacionRequestException(String.join("; ", errores));
        }

        return tipoNormalizado;
    }

    private boolean esTipoValido(String tipo) {
        return TIPO_VACUNA.equals(tipo)
                || TIPO_MEDICACION.equals(tipo)
                || TIPO_CONSULTA_VETERINARIA.equals(tipo)
                || TIPO_PESO.equals(tipo);
    }

    private String normalizarTipo(String tipo) {
        if (tipo == null) {
            return "";
        }

        String sinAcentos = Normalizer.normalize(tipo.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private boolean estaEnBlanco(String valor) {
        return valor == null || valor.isBlank();
    }

    private void validarFechaNoFutura(Instant fecha) {
        if (fecha == null) {
            return;
        }

        Instant finDelDiaActual = LocalDate.now(ZONA_ARGENTINA)
                .plusDays(1)
                .atStartOfDay(ZONA_ARGENTINA)
                .minusNanos(1)
                .toInstant();

        if (fecha.isAfter(finDelDiaActual)) {
            throw new ReglaNegocioException("La fecha no puede ser posterior al dia actual.");
        }
    }

    private void recalcularPesoActual(Mascota mascota) {
        if (mascota == null || mascota.getIdMascota() == null) {
            return;
        }

        BigDecimal pesoActual = eventoClinicoRepository
                .findFirstByMascota_IdMascotaAndTipoAndFechaEliminacionIsNullOrderByFechaDesc(
                        mascota.getIdMascota(),
                        TIPO_PESO)
                .map(EventoClinico::getValorPeso)
                .orElse(null);

        mascota.setPesoActual(pesoActual);
        mascotaRepository.save(mascota);
    }

    private Mascota buscarMascotaActiva(UUID id) {
        return mascotaRepository.findByIdMascotaAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("La mascota no existe o fue eliminada."));
    }

    private EventoClinico buscarEventoActivo(UUID id) {
        return eventoClinicoRepository.findByIdEventoAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El evento clinico no existe."));
    }

    private void validarAccesoEvento(EventoClinico evento, UUID usuarioAutenticadoId) {
        validarAccesoMascota(evento.getMascota(), usuarioAutenticadoId);
    }

    private void validarAccesoMascota(Mascota mascota, UUID usuarioAutenticadoId) {
        UUID propietarioId = mascota != null && mascota.getPropietario() != null ? mascota.getPropietario().getIdUsuario() : null;
        if (!usuarioAutenticadoId.equals(propietarioId)) {
            throw new AccesoDenegadoException("No tenes permisos para realizar esta accion sobre esta mascota");
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
                evento.getCreadoEn()
        );
    }
}