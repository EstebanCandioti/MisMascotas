package com.MisMascotas.backend.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.DTO.LogAuditoriaResponseDTO;
import com.MisMascotas.backend.Entity.LogAuditoria;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Mapper.LogAuditoriaMapper;
import com.MisMascotas.backend.Repository.LogAuditoriaRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;

@Service
@Transactional
public class LogAuditoriaService {

    private final LogAuditoriaRepository logAuditoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public LogAuditoriaService(LogAuditoriaRepository logAuditoriaRepository, UsuarioRepository usuarioRepository) {
        this.logAuditoriaRepository = logAuditoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // CU24 E.1: la auditoria usa una transaccion independiente para que un fallo al registrar el log no revierta la accion principal.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String entidadAfectada, TipoAccionAuditoria tipoAccion, UUID idEntidad,
            String valorAnterior, String valorNuevo) {
        LogAuditoria log = LogAuditoria.builder()
                .actor(obtenerUsuarioActualONull())
                .tipoAccion(tipoAccion)
                .entidadAfectada(entidadAfectada)
                .idEntidad(idEntidad)
                .valorAnterior(valorAnterior)
                .valorNuevo(valorNuevo)
                .build();

        logAuditoriaRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<LogAuditoriaResponseDTO> obtenerPorEntidad(String entidadAfectada, UUID idEntidad) {
        return logAuditoriaRepository
                .findByEntidadAfectadaAndIdEntidadOrderByFechaHoraDesc(entidadAfectada, idEntidad)
                .stream()
                .map(LogAuditoriaMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LogAuditoriaResponseDTO> obtenerPorActor(UUID actorId) {
        return logAuditoriaRepository.findByActor_IdUsuarioOrderByFechaHoraDesc(actorId)
                .stream()
                .map(LogAuditoriaMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<LogAuditoriaResponseDTO> listarConFiltros(
            String entidadAfectada,
            TipoAccionAuditoria tipoAccion,
            Instant desde,
            Instant hasta,
            Pageable pageable) {
        String entidadNormalizada = entidadAfectada != null && !entidadAfectada.isBlank()
                ? entidadAfectada.trim()
                : null;

        return logAuditoriaRepository
                .buscarConFiltros(entidadNormalizada, tipoAccion, desde, hasta, pageable)
                .map(LogAuditoriaMapper::toDTO);
    }

    private Usuario obtenerUsuarioActualONull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || !auth.isAuthenticated()) {
            return null;
        }

        return usuarioRepository.findByEmail(auth.getName());
    }
}
