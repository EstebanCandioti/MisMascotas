package com.MisMascotas.backend.Audit;

import java.lang.reflect.Method;
import java.util.UUID;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Service.LogAuditoriaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Aspect
@Component
public class AuditoriaAspect {

    private static final Logger logger = LoggerFactory.getLogger(AuditoriaAspect.class);

    private final LogAuditoriaService logAuditoriaService;
    private final ObjectMapper objectMapper;
    private final ThreadLocal<String> valorAnteriorHolder = new ThreadLocal<>();

    public AuditoriaAspect(LogAuditoriaService logAuditoriaService) {
        this.logAuditoriaService = logAuditoriaService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Before("@annotation(auditable)")
    public void capturarEstadoAnterior(JoinPoint joinPoint, Auditable auditable) {
        try {
            if (!necesitaEstadoAnterior(auditable.accion())) {
                return;
            }

            UUID idEntidad = extraerIdDesdeArgumentos(joinPoint.getArgs());
            if (idEntidad == null) {
                return;
            }

            Object estadoAnterior = obtenerEstadoActual(joinPoint, idEntidad);
            if (estadoAnterior != null) {
                valorAnteriorHolder.set(objectMapper.writeValueAsString(estadoAnterior));
            }
        } catch (Exception ex) {
            logger.warn("No se pudo capturar estado anterior para auditoria: {}", ex.getMessage());
            valorAnteriorHolder.remove();
        }
    }

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "resultado")
    public void registrarAuditoria(JoinPoint joinPoint, Auditable auditable, Object resultado) {
        try {
            UUID idEntidad = extraerIdEntidad(joinPoint, resultado, auditable.accion());
            if (idEntidad == null) {
                logger.warn("No se pudo extraer idEntidad para auditar {} {}", auditable.accion(), auditable.entidad());
                return;
            }

            String valorAnterior = auditable.accion() == TipoAccionAuditoria.CREATE ? null : valorAnteriorHolder.get();
            String valorNuevo = auditable.accion() == TipoAccionAuditoria.DELETE ? null : serializar(resultado);
            valorAnteriorHolder.remove();

            logAuditoriaService.registrar(
                    auditable.entidad(),
                    auditable.accion(),
                    idEntidad,
                    valorAnterior,
                    valorNuevo);
        } catch (Exception ex) {
            logger.error("No se pudo registrar auditoria: {}", ex.getMessage());
            valorAnteriorHolder.remove();
        }
    }

    private boolean necesitaEstadoAnterior(TipoAccionAuditoria accion) {
        return accion == TipoAccionAuditoria.UPDATE
                || accion == TipoAccionAuditoria.DELETE
                || accion == TipoAccionAuditoria.RESTORE
                || accion == TipoAccionAuditoria.ACTIVATE
                || accion == TipoAccionAuditoria.DEACTIVATE
                || accion == TipoAccionAuditoria.SHARE
                || accion == TipoAccionAuditoria.UNSHARE
                || accion == TipoAccionAuditoria.CONFIRM;
    }

    private UUID extraerIdEntidad(JoinPoint joinPoint, Object resultado, TipoAccionAuditoria accion) {
        if (accion == TipoAccionAuditoria.CREATE) {
            return extraerIdDesdeObjeto(resultado);
        }

        UUID idDesdeArgumentos = extraerIdDesdeArgumentos(joinPoint.getArgs());
        return idDesdeArgumentos != null ? idDesdeArgumentos : extraerIdDesdeObjeto(resultado);
    }

    private UUID extraerIdDesdeArgumentos(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        if (args[0] instanceof UUID id) {
            return id;
        }

        return extraerIdDesdeObjeto(args[0]);
    }

    private UUID extraerIdDesdeObjeto(Object objeto) {
        if (objeto == null) {
            return null;
        }

        if (objeto instanceof UUID id) {
            return id;
        }

        for (Method method : objeto.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && method.getName().startsWith("getId")) {
                try {
                    Object valor = method.invoke(objeto);
                    if (valor instanceof UUID id) {
                        return id;
                    }
                } catch (Exception ex) {
                    logger.debug("No se pudo leer {} para auditoria", method.getName());
                }
            }
        }

        return null;
    }

    private Object obtenerEstadoActual(JoinPoint joinPoint, UUID idEntidad) {
        try {
            Object target = joinPoint.getTarget();
            Method obtenerPorId = target.getClass().getMethod("obtenerPorId", UUID.class);
            return obtenerPorId.invoke(target, idEntidad);
        } catch (Exception ex) {
            logger.debug("No se pudo obtener estado actual para auditoria: {}", ex.getMessage());
            return null;
        }
    }

    private String serializar(Object objeto) {
        if (objeto == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(objeto);
        } catch (Exception ex) {
            logger.warn("No se pudo serializar valor de auditoria: {}", ex.getMessage());
            return null;
        }
    }
}