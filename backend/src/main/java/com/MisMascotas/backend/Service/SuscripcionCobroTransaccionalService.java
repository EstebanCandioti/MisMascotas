package com.MisMascotas.backend.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.Entity.Estado;
import com.MisMascotas.backend.Entity.Pago;
import com.MisMascotas.backend.Entity.Suscripcion;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Exception.EstadoNoConfiguradoException;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PagoConsultado;
import com.MisMascotas.backend.Repository.EstadoRepository;
import com.MisMascotas.backend.Repository.PagoRepository;
import com.MisMascotas.backend.Repository.SuscripcionRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SuscripcionCobroTransaccionalService {

    private final UsuarioRepository usuarioRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final PagoRepository pagoRepository;
    private final EstadoRepository estadoRepository;
    private final EventoAuditoriaPendienteService auditoria;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public ContextoCobro buscar(UUID usuarioId, UUID idSuscripcion) {
        Suscripcion suscripcion = buscarPropia(usuarioId, idSuscripcion);
        return new ContextoCobro(idSuscripcion, suscripcion.getTipoPlan(),
                suscripcion.getIdSuscripcionMercadopago(), esEstado(suscripcion, "PENDIENTE"),
                resultadoPersistido(suscripcion));
    }

    @Transactional
    public ResultadoCobro confirmar(UUID usuarioId, ContextoCobro contexto, PagoConsultado verificado,
            Instant vencimiento) {
        // Mismo orden que alta/reemplazo: primero usuario, despues suscripcion.
        var usuario = usuarioRepository.findByIdConBloqueo(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        Suscripcion suscripcion = buscarPropia(usuarioId, contexto.idSuscripcion());
        ResultadoCobro persistido = resultadoPersistido(suscripcion);
        if (persistido != null) {
            return persistido;
        }
        if (!esEstado(suscripcion, "PENDIENTE")
                || !Objects.equals(contexto.idMercadoPago(), suscripcion.getIdSuscripcionMercadopago())
                || !Objects.equals(contexto.tipoPlan(), suscripcion.getTipoPlan())) {
            return ResultadoCobro.sinPago(contexto.idSuscripcion(), EstadoCobro.PENDIENTE_CONCILIACION);
        }

        var existente = pagoRepository.findByIdTransaccionMercadopago(verificado.id());
        if (existente.isPresent()
                || !pagoRepository.findBySuscripcion_IdSuscripcionOrderByFechaPagoAsc(contexto.idSuscripcion()).isEmpty()) {
            // Un pago previo sin activacion coherente requiere revision, no otra escritura.
            return ResultadoCobro.sinPago(contexto.idSuscripcion(), EstadoCobro.PENDIENTE_CONCILIACION);
        }

        Estado aprobado = estado("PAGO", "APROBADO");
        Estado activa = estado("SUSCRIPCION", "ACTIVA");
        SnapshotSuscripcion anterior = snapshot(suscripcion);
        Pago pago = new Pago();
        pago.setSuscripcion(suscripcion);
        pago.setEstadoCatalogo(aprobado);
        pago.setIdTransaccionMercadopago(verificado.id());
        pago.setFechaPago(verificado.fechaAprobacion());
        pago.setMonto(verificado.importe());
        pagoRepository.saveAndFlush(pago);

        suscripcion.setEstadoCatalogo(activa);
        suscripcion.setFechaVencimiento(vencimiento);
        suscripcion.setProximoRecargo(contexto.proximoRecargo());
        suscripcionRepository.saveAndFlush(suscripcion);
        var cobertura = PremiumService.evaluar(usuario,
                suscripcionRepository.findAllByUsuario_IdUsuario(usuarioId),
                pagoRepository.buscarSuscripcionesConPagoAprobado(usuarioId), reloj.instant());
        usuario.setEsPremium(cobertura.estado().esPremium());
        usuarioRepository.saveAndFlush(usuario);

        auditoria.encolar(usuarioId, "pago", TipoAccionAuditoria.CREATE, pago.getIdPago(), null,
                new SnapshotPago(pago.getIdPago(), suscripcion.getIdSuscripcion(), verificado.id(),
                        pago.getMonto(), pago.getFechaPago(), "APROBADO"));
        auditoria.encolar(usuarioId, "suscripcion", TipoAccionAuditoria.ACTIVATE,
                suscripcion.getIdSuscripcion(), anterior, snapshot(suscripcion));
        return resultado(suscripcion, pago, EstadoCobro.ACTIVADA);
    }

    private ResultadoCobro resultadoPersistido(Suscripcion suscripcion) {
        if (!esEstado(suscripcion, "ACTIVA") && suscripcion.getFechaVencimiento() == null) return null;
        var pagos = pagoRepository.findBySuscripcion_IdSuscripcionOrderByFechaPagoAsc(suscripcion.getIdSuscripcion());
        if (!esEstado(suscripcion, "ACTIVA") && pagos.isEmpty()) return null;
        if (pagos.isEmpty() || suscripcion.getFechaVencimiento() == null) {
            return ResultadoCobro.sinPago(suscripcion.getIdSuscripcion(), EstadoCobro.PENDIENTE_CONCILIACION);
        }
        Pago primero = pagos.getFirst();
        Estado estado = primero.getEstadoCatalogo();
        if (estado == null || !"PAGO".equals(estado.getEntidad()) || !"APROBADO".equals(estado.getNombre())) {
            return ResultadoCobro.sinPago(suscripcion.getIdSuscripcion(), EstadoCobro.PENDIENTE_CONCILIACION);
        }
        return resultado(suscripcion, primero, EstadoCobro.YA_CONFIRMADA);
    }

    private ResultadoCobro resultado(Suscripcion suscripcion, Pago pago, EstadoCobro estado) {
        return new ResultadoCobro(suscripcion.getIdSuscripcion(), estado, pago.getIdPago(),
                pago.getIdTransaccionMercadopago(), pago.getFechaPago(), suscripcion.getFechaVencimiento());
    }

    private Suscripcion buscarPropia(UUID usuarioId, UUID idSuscripcion) {
        return suscripcionRepository.findByIdSuscripcionAndUsuario_IdUsuario(idSuscripcion, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Suscripcion no encontrada"));
    }

    private boolean esEstado(Suscripcion suscripcion, String nombre) {
        Estado estado = suscripcion.getEstadoCatalogo();
        return estado != null && "SUSCRIPCION".equals(estado.getEntidad()) && nombre.equals(estado.getNombre());
    }

    private Estado estado(String entidad, String nombre) {
        return estadoRepository.findByEntidadAndNombre(entidad, nombre)
                .orElseThrow(() -> new EstadoNoConfiguradoException(entidad, nombre));
    }

    private SnapshotSuscripcion snapshot(Suscripcion suscripcion) {
        return new SnapshotSuscripcion(suscripcion.getIdSuscripcion(), suscripcion.getTipoPlan(),
                suscripcion.getEstadoCatalogo().getNombre(), suscripcion.getFechaInicio(),
                suscripcion.getFechaVencimiento(), suscripcion.getProximoRecargo());
    }

    public enum EstadoCobro {
        ACTIVADA, YA_CONFIRMADA, SIN_COBRO_APROBADO, NO_SE_PUDO_VERIFICAR, PENDIENTE_CONCILIACION
    }

    public record ResultadoCobro(UUID idSuscripcion, EstadoCobro estado, UUID idPago, String idPagoMercadoPago,
            Instant fechaPago, Instant fechaVencimiento) {
        public static ResultadoCobro sinPago(UUID idSuscripcion, EstadoCobro estado) {
            return new ResultadoCobro(idSuscripcion, estado, null, null, null, null);
        }
    }

    public record ContextoCobro(UUID idSuscripcion, String tipoPlan, String idMercadoPago, boolean pendiente,
            ResultadoCobro resultadoPersistido, Instant proximoRecargo) {
        public ContextoCobro(UUID idSuscripcion, String tipoPlan, String idMercadoPago, boolean pendiente,
                ResultadoCobro resultadoPersistido) {
            this(idSuscripcion, tipoPlan, idMercadoPago, pendiente, resultadoPersistido, null);
        }
        public ContextoCobro conProximoRecargo(Instant fecha) {
            return new ContextoCobro(idSuscripcion, tipoPlan, idMercadoPago, pendiente, resultadoPersistido, fecha);
        }
    }

    public record SnapshotPago(UUID idPago, UUID idSuscripcion, String idPagoMercadoPago, BigDecimal monto,
            Instant fechaPago, String estado) {
    }

    public record SnapshotSuscripcion(UUID idSuscripcion, String tipoPlan, String estado, Instant fechaReserva,
            Instant fechaVencimiento, Instant proximoRecargo) {
    }
}
