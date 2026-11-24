package com.MisMascotas.backend.Service;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.Entity.EventoAuditoriaPendiente;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Repository.EventoAuditoriaPendienteRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class EventoAuditoriaPendienteService {

    private final EventoAuditoriaPendienteRepository eventoRepository;
    private final UsuarioRepository usuarioRepository;
    private final JsonMapper jsonMapper;

    public EventoAuditoriaPendienteService(
            EventoAuditoriaPendienteRepository eventoRepository,
            UsuarioRepository usuarioRepository,
            JsonMapper jsonMapper) {
        this.eventoRepository = eventoRepository;
        this.usuarioRepository = usuarioRepository;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Encola la auditoria dentro de la transaccion que modifica el dato de negocio.
     * No consulta el contexto de seguridad: el llamador debe informar el actor.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID encolar(
            UUID actorId,
            String entidadAfectada,
            TipoAccionAuditoria tipoAccion,
            UUID idEntidad,
            Object valorAnterior,
            Object valorNuevo) {
        Objects.requireNonNull(entidadAfectada, "La entidad auditada es obligatoria");
        Objects.requireNonNull(tipoAccion, "La accion auditada es obligatoria");

        EventoAuditoriaPendiente evento = EventoAuditoriaPendiente.builder()
                .actor(actorId == null ? null : usuarioRepository.getReferenceById(actorId))
                .entidadAfectada(entidadAfectada)
                .tipoAccion(tipoAccion)
                .idEntidad(idEntidad)
                .valorAnterior(serializar(valorAnterior))
                .valorNuevo(serializar(valorNuevo))
                .build();

        return eventoRepository.save(evento).getIdEvento();
    }

    private String serializar(Object valor) {
        if (valor == null) {
            return null;
        }
        try {
            return jsonMapper.writeValueAsString(valor);
        } catch (JacksonException ex) {
            throw new IllegalStateException("No se pudo serializar el evento de auditoria", ex);
        }
    }
}
