package com.MisMascotas.backend.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Exception.EstadoNoConfiguradoException;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.*;
import com.MisMascotas.backend.Repository.*;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.ResultadoProcesamiento;
import lombok.RequiredArgsConstructor;

/** Comparte el bloqueo por usuario con alta, reemplazo y vencimiento. */
@Service
@RequiredArgsConstructor
public class SuscripcionCicloTransaccionalService {
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private final UsuarioRepository usuarios;
    private final SuscripcionRepository suscripciones;
    private final PagoRepository pagos;
    private final EstadoRepository estados;
    private final EventoAuditoriaPendienteService auditoria;
    private final Clock reloj;

    @Transactional
    public ResultadoProcesamiento registrar(UUID usuarioId, UUID id, String tipoPlan, FacturaAutorizada factura,
            PagoConsultado verificado, PreapprovalConsultado remoto, int meses) {
        Usuario usuario = bloquear(usuarioId);
        Suscripcion local = propia(usuarioId, id);
        if (!identidad(local, tipoPlan, remoto) || local.isReemplazada()
                || (es(local, "CANCELADA") && local.getEstadoMercadopago() == null
                    && !pagos.existePagoAprobado(id)))
            return ResultadoProcesamiento.revision("INTENTO_REEMPLAZADO_O_INCONSISTENTE");
        var porPago = pagos.findByIdTransaccionMercadopago(verificado.id());
        var porFactura = pagos.findByIdFacturaMercadopago(factura.id());
        if (porPago.isPresent() || porFactura.isPresent()) {
            Pago previo = porPago.orElseGet(() -> porFactura.orElseThrow());
            if (!id.equals(previo.getSuscripcion().getIdSuscripcion())
                    || !verificado.id().equals(previo.getIdTransaccionMercadopago())
                    || (previo.getIdFacturaMercadopago() != null && !factura.id().equals(previo.getIdFacturaMercadopago()))
                    || previo.getEstadoCatalogo() == null || !"APROBADO".equals(previo.getEstadoCatalogo().getNombre())
                    || previo.getMonto().compareTo(verificado.importe()) != 0
                    || !previo.getFechaPago().equals(verificado.fechaAprobacion()) || local.getFechaVencimiento() == null)
                return ResultadoProcesamiento.revision("PAGO_O_FACTURA_YA_ASOCIADOS");
            // Vincula la factura de un primer cobro confirmado manualmente antes del webhook.
            if (previo.getIdFacturaMercadopago() == null) {
                previo.setIdFacturaMercadopago(factura.id());
                pagos.saveAndFlush(previo);
            }
            return ResultadoProcesamiento.completada("YA_CONFIRMADA");
        }
        boolean primera = !pagos.existePagoAprobado(id);
        if ((!primera && local.getFechaVencimiento() == null)
                || (primera && !es(local, "PENDIENTE") && !es(local, "CANCELADA")))
            return ResultadoProcesamiento.revision("SUSCRIPCION_INCONSISTENTE");
        Snapshot anterior = snapshot(local);
        Instant base = verificado.fechaAprobacion();
        if (!primera && local.getFechaVencimiento().isAfter(base)) base = local.getFechaVencimiento();
        Instant vencimiento = base.atZone(ZONA).plusMonths(meses).toInstant();
        Pago pago = new Pago();
        pago.setSuscripcion(local); pago.setMonto(verificado.importe()); pago.setFechaPago(verificado.fechaAprobacion());
        pago.setEstadoCatalogo(estado("PAGO", "APROBADO"));
        pago.setIdTransaccionMercadopago(verificado.id()); pago.setIdFacturaMercadopago(factura.id());
        pagos.saveAndFlush(pago);
        local.setFechaVencimiento(vencimiento);
        aplicarRemoto(local, remoto);
        if ("cancelled".equals(local.getEstadoMercadopago()) || es(local, "CANCELADA")) local.setEstadoCatalogo(estado("SUSCRIPCION", "CANCELADA"));
        else local.setEstadoCatalogo(estado("SUSCRIPCION", "ACTIVA"));
        // No concede una nueva excepcion de vigencia a pagos confirmados meses despues.
        suscripciones.saveAndFlush(local);
        var cobertura = PremiumService.evaluar(usuario, suscripciones.findAllByUsuario_IdUsuario(usuarioId),
                pagos.buscarSuscripcionesConPagoAprobado(usuarioId), reloj.instant());
        usuario.setEsPremium(cobertura.estado().esPremium());
        usuarios.saveAndFlush(usuario);
        auditoria.encolar(null, "pago", TipoAccionAuditoria.CREATE, pago.getIdPago(), null,
                new PagoAuditado(pago.getIdPago(), id, factura.id(), verificado.id(), verificado.fechaAprobacion(),
                        verificado.importe(), "WEBHOOK_MERCADO_PAGO"));
        auditoria.encolar(null, "suscripcion", primera ? TipoAccionAuditoria.ACTIVATE : TipoAccionAuditoria.UPDATE,
                id, anterior, snapshot(local));
        return ResultadoProcesamiento.completada(primera ? "ACTIVADA" : "RENOVADA");
    }

    @Transactional
    public ResultadoProcesamiento sincronizar(UUID usuarioId, UUID id, String tipoPlan, PreapprovalConsultado remoto) {
        bloquear(usuarioId);
        Suscripcion local = propia(usuarioId, id);
        if (!identidad(local, tipoPlan, remoto)) return ResultadoProcesamiento.revision("SUSCRIPCION_INCONSISTENTE");
        if (local.isReemplazada() || (es(local, "CANCELADA") && local.getEstadoMercadopago() == null
                && !pagos.existePagoAprobado(id)))
            return ResultadoProcesamiento.completada("INTENTO_REEMPLAZADO");
        if (anteriorAlPersistido(local, remoto)) return ResultadoProcesamiento.completada("ESTADO_REMOTO_YA_ACTUALIZADO");
        Snapshot anterior = snapshot(local);
        aplicarRemoto(local, remoto);
        if (cancelada(remoto.estado())) local.setEstadoCatalogo(estado("SUSCRIPCION", "CANCELADA"));
        // Autorizar o reactivar el mandato no acredita un nuevo pago.
        else if ("authorized".equals(remoto.estado()) && es(local, "CANCELADA")) {
            boolean pagada = pagos.existePagoAprobado(id);
            local.setEstadoCatalogo(estado("SUSCRIPCION",
                    PremiumService.tieneCoberturaVigente(local, pagada, reloj.instant()) ? "ACTIVA" :
                    pagada ? "VENCIDA" : "PENDIENTE"));
        }
        Snapshot nuevo = snapshot(local);
        if (!anterior.equals(nuevo)) {
            suscripciones.saveAndFlush(local);
            auditoria.encolar(null, "suscripcion", TipoAccionAuditoria.UPDATE, id, anterior, nuevo);
        }
        String motivo = switch (remoto.estado()) {
            case "pending" -> "SUSCRIPCION_PENDIENTE";
            case "authorized" -> "paused".equals(anterior.estadoMp()) ? "SUSCRIPCION_REACTIVADA" : "SUSCRIPCION_AUTORIZADA";
            case "paused" -> "SUSCRIPCION_PAUSADA";
            default -> "SUSCRIPCION_CANCELADA";
        };
        return ResultadoProcesamiento.completada(motivo);
    }

    private boolean identidad(Suscripcion local, String plan, PreapprovalConsultado remoto) {
        return local.getEstadoCatalogo() != null && "SUSCRIPCION".equals(local.getEstadoCatalogo().getEntidad())
                && Objects.equals(local.getTipoPlan(), plan) && Objects.equals(local.getIdSuscripcionMercadopago(), remoto.id())
                && local.getIdSuscripcion().toString().equals(remoto.referenciaExterna());
    }
    private boolean anteriorAlPersistido(Suscripcion s, PreapprovalConsultado remoto) {
        return s.getActualizadaMercadopagoEn() != null && remoto.ultimaModificacion() != null
                && remoto.ultimaModificacion().isBefore(s.getActualizadaMercadopagoEn());
    }
    private void aplicarRemoto(Suscripcion local, PreapprovalConsultado remoto) {
        if (anteriorAlPersistido(local, remoto)) return;
        local.setEstadoMercadopago(cancelada(remoto.estado()) ? "cancelled" : remoto.estado());
        local.setProximoRecargo(remoto.proximoPago());
        if (remoto.ultimaModificacion() != null) local.setActualizadaMercadopagoEn(remoto.ultimaModificacion());
        if (cancelada(remoto.estado()) && local.getFechaCancelacion() == null) local.setFechaCancelacion(reloj.instant());
    }
    private Usuario bloquear(UUID id) { return usuarios.findByIdConBloqueo(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado")); }
    private Suscripcion propia(UUID usuarioId, UUID id) { return suscripciones.findByIdSuscripcionAndUsuario_IdUsuario(id, usuarioId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Suscripcion no encontrada")); }
    private Estado estado(String entidad, String nombre) { return estados.findByEntidadAndNombre(entidad, nombre)
            .orElseThrow(() -> new EstadoNoConfiguradoException(entidad, nombre)); }
    private boolean es(Suscripcion s, String nombre) { return s.getEstadoCatalogo() != null && nombre.equals(s.getEstadoCatalogo().getNombre()); }
    private boolean cancelada(String estado) { return "cancelled".equals(estado) || "canceled".equals(estado); }
    private Snapshot snapshot(Suscripcion s) { return new Snapshot(s.getEstadoCatalogo().getNombre(),
            s.getEstadoMercadopago(), s.getFechaVencimiento(), s.getProximoRecargo(), s.getFechaCancelacion(), "WEBHOOK_MERCADO_PAGO"); }
    private record Snapshot(String estado, String estadoMp, Instant vencimiento, Instant proximoCobro,
            Instant cancelacion, String origen) {}
    private record PagoAuditado(UUID idPago, UUID idSuscripcion, String idFacturaMp, String idPagoMp,
            Instant fechaPago, java.math.BigDecimal monto, String origen) {}
}
