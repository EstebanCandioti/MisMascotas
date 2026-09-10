package com.MisMascotas.backend.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

@Service
@Transactional
public class LogAuditoriaService {

    private final LogAuditoriaRepository logAuditoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final EntityManager entityManager;

    public LogAuditoriaService(LogAuditoriaRepository logAuditoriaRepository, UsuarioRepository usuarioRepository,
            EntityManager entityManager) {
        this.logAuditoriaRepository = logAuditoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.entityManager = entityManager;
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

        StringBuilder filtros = new StringBuilder(" FROM LogAuditoria log WHERE 1 = 1");
        Map<String, Object> parametros = new HashMap<>();

        if (entidadNormalizada != null) {
            filtros.append(" AND LOWER(log.entidadAfectada) = LOWER(:entidadAfectada)");
            parametros.put("entidadAfectada", entidadNormalizada);
        }

        if (tipoAccion != null) {
            filtros.append(" AND log.tipoAccion = :tipoAccion");
            parametros.put("tipoAccion", tipoAccion);
        }

        if (desde != null) {
            filtros.append(" AND log.fechaHora >= :desde");
            parametros.put("desde", desde);
        }

        if (hasta != null) {
            filtros.append(" AND log.fechaHora <= :hasta");
            parametros.put("hasta", hasta);
        }

        TypedQuery<LogAuditoria> query = entityManager.createQuery(
                "SELECT log" + filtros + construirOrderBy(pageable.getSort()),
                LogAuditoria.class);
        TypedQuery<Long> countQuery = entityManager.createQuery(
                "SELECT COUNT(log)" + filtros,
                Long.class);

        parametros.forEach((nombre, valor) -> {
            query.setParameter(nombre, valor);
            countQuery.setParameter(nombre, valor);
        });

        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        Page<LogAuditoria> pagina = new PageImpl<>(
                query.getResultList(),
                pageable,
                countQuery.getSingleResult());

        return pagina.map(LogAuditoriaMapper::toDTO);
    }

    private String construirOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return " ORDER BY log.fechaHora DESC";
        }

        List<String> ordenes = sort.stream()
                .map(order -> "log." + order.getProperty() + " " + order.getDirection().name())
                .toList();

        return " ORDER BY " + String.join(", ", ordenes);
    }

    private Usuario obtenerUsuarioActualONull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || !auth.isAuthenticated()) {
            return null;
        }

        return usuarioRepository.findByEmail(auth.getName());
    }
}