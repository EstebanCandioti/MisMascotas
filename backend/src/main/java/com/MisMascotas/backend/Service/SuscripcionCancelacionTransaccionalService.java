package com.MisMascotas.backend.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.MisMascotas.backend.DTO.CancelarSuscripcionResponseDTO;
import com.MisMascotas.backend.DTO.CancelarSuscripcionResponseDTO.Resultado;
import com.MisMascotas.backend.Entity.Suscripcion;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Exception.*;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PreapprovalConsultado;
import com.MisMascotas.backend.Repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** Transacciones cortas; comparte el bloqueo por usuario con cobros y vencimientos. */
@Service
@RequiredArgsConstructor
public class SuscripcionCancelacionTransaccionalService {
    private final UsuarioRepository usuarios;
    private final SuscripcionRepository suscripciones;
    private final EstadoRepository estados;
    private final EventoAuditoriaPendienteService auditoria;
    private final Clock reloj;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Candidata buscar(UUID usuarioId, UUID id) {
        var s = propia(usuarioId, id);
        return new Candidata(s.getIdSuscripcion(), s.getIdSuscripcionMercadopago(), s.getTipoPlan(),
                elegible(s), s.getActualizadaMercadopagoEn());
    }

    @Transactional
    public CancelarSuscripcionResponseDTO confirmar(UUID usuarioId, Candidata candidata, PreapprovalConsultado remoto) {
        usuarios.findByIdConBloqueo(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        var s = propia(usuarioId, candidata.idSuscripcion());
        // La petición HTTP puede conservar la entidad de la lectura inicial mediante Open EntityManager in View.
        // Recargar bajo el bloqueo compartido evita sobrescribir cambios confirmados durante las llamadas a MP.
        entityManager.refresh(s);
        if (!elegible(s)) return CancelarSuscripcionResponseDTO.sinConfirmar(s.getIdSuscripcion(), Resultado.NO_CANCELABLE);
        if (!Objects.equals(s.getTipoPlan(), candidata.tipoPlan())
                || !Objects.equals(s.getIdSuscripcionMercadopago(), candidata.idMercadoPago())
                || remoto == null || !Objects.equals(s.getIdSuscripcionMercadopago(), remoto.id())
                || !s.getIdSuscripcion().toString().equals(remoto.referenciaExterna())
                || !("cancelled".equals(remoto.estado()) || "canceled".equals(remoto.estado()))
                || (s.getActualizadaMercadopagoEn() != null && remoto.ultimaModificacion() != null
                    && remoto.ultimaModificacion().isBefore(s.getActualizadaMercadopagoEn()))
                || (remoto.ultimaModificacion() == null
                    && !Objects.equals(candidata.actualizadaMercadoPagoEn(), s.getActualizadaMercadopagoEn()))) {
            return CancelarSuscripcionResponseDTO.sinConfirmar(s.getIdSuscripcion(), Resultado.REQUIERE_CONCILIACION);
        }
        var anterior = snapshot(s);
        boolean yaCancelada = "CANCELADA".equals(s.getEstadoCatalogo().getNombre())
                && "cancelled".equals(s.getEstadoMercadopago()) && s.getFechaCancelacion() != null;
        s.setEstadoCatalogo(estados.findByEntidadAndNombre("SUSCRIPCION", "CANCELADA")
                .orElseThrow(() -> new EstadoNoConfiguradoException("SUSCRIPCION", "CANCELADA")));
        s.setEstadoMercadopago("cancelled");
        if (s.getFechaCancelacion() == null) s.setFechaCancelacion(reloj.instant());
        if (remoto.ultimaModificacion() != null) s.setActualizadaMercadopagoEn(remoto.ultimaModificacion());
        s.setProximoRecargo(remoto.proximoPago());
        var nuevo = snapshot(s);
        if (!anterior.equals(nuevo)) {
            suscripciones.saveAndFlush(s);
            auditoria.encolar(usuarioId, "suscripcion", TipoAccionAuditoria.UPDATE, s.getIdSuscripcion(), anterior, nuevo);
        }
        // Cancelar el mandato no modifica pagos, vencimiento ni el indicador Premium del usuario.
        return new CancelarSuscripcionResponseDTO(s.getIdSuscripcion(),
                yaCancelada ? Resultado.YA_CANCELADA : Resultado.CANCELADA,
                "CANCELADA", s.getFechaCancelacion(), s.getFechaVencimiento());
    }

    private Suscripcion propia(UUID usuarioId, UUID id) {
        return suscripciones.findByIdSuscripcionAndUsuario_IdUsuario(id, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Suscripcion no encontrada"));
    }

    private boolean elegible(Suscripcion s) {
        return !s.isReemplazada() && s.getEstadoCatalogo() != null
                && "SUSCRIPCION".equals(s.getEstadoCatalogo().getEntidad())
                && Set.of("ACTIVA", "VENCIDA", "CANCELADA").contains(s.getEstadoCatalogo().getNombre());
    }

    private Snapshot snapshot(Suscripcion s) {
        return new Snapshot(s.getEstadoCatalogo().getNombre(), s.getEstadoMercadopago(), s.getFechaVencimiento(),
                s.getProximoRecargo(), s.getFechaCancelacion(), s.getActualizadaMercadopagoEn(), "CANCELACION_USUARIO");
    }
    public record Candidata(UUID idSuscripcion, String idMercadoPago, String tipoPlan, boolean elegible,
            Instant actualizadaMercadoPagoEn) {}
    private record Snapshot(String estado, String estadoMp, Instant vencimiento, Instant proximoCobro,
            Instant cancelacion, Instant actualizadaMp, String origen) {}
}
