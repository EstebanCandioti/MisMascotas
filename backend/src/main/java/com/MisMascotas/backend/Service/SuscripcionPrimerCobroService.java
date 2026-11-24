package com.MisMascotas.backend.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.Exception.MercadoPagoException;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PagoConsultado;
import com.MisMascotas.backend.Service.SuscripcionCobroTransaccionalService.EstadoCobro;
import com.MisMascotas.backend.Service.SuscripcionCobroTransaccionalService.ResultadoCobro;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SuscripcionPrimerCobroService {

    private static final Logger logger = LoggerFactory.getLogger(SuscripcionPrimerCobroService.class);
    private static final ZoneId ZONA_VIGENCIA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final SuscripcionCobroTransaccionalService transacciones;
    private final CatalogoPlanesSuscripcion catalogo;
    private final MercadoPagoGateway proveedor;

    // Suspende una transaccion del llamador para no mantenerla durante consultas HTTP.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResultadoCobro confirmar(UUID usuarioId, UUID idSuscripcion) {
        return confirmarConDetalle(usuarioId, idSuscripcion).resultado();
    }

    /** Conserva el diagnostico para la bandeja sin ampliar el DTO de los endpoints. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VerificacionCobro confirmarConDetalle(UUID usuarioId, UUID idSuscripcion) {
        var contexto = transacciones.buscar(usuarioId, idSuscripcion);
        if (contexto.resultadoPersistido() != null) {
            return new VerificacionCobro(contexto.resultadoPersistido(), TipoVerificacion.CONFIRMADA, null);
        }
        if (!contexto.pendiente() || contexto.idMercadoPago() == null || contexto.idMercadoPago().isBlank()) {
            return sinPago(idSuscripcion, EstadoCobro.PENDIENTE_CONCILIACION, TipoVerificacion.CONCILIACION);
        }

        PagoConsultado primero = null;
        Instant vencimiento;
        try {
            var plan = catalogo.resolver(contexto.tipoPlan());
            var remoto = proveedor.consultarPreapproval(contexto.idMercadoPago());
            if (remoto == null || !contexto.idMercadoPago().equals(remoto.id())
                    || !idSuscripcion.toString().equals(remoto.referenciaExterna())
                    || remoto.importe() == null || plan.importe().compareTo(remoto.importe()) != 0
                    || !plan.moneda().equals(remoto.moneda()) || plan.frecuencia() != remoto.frecuencia()
                    || !plan.tipoFrecuencia().equals(remoto.tipoFrecuencia())) {
                return sinPago(idSuscripcion, EstadoCobro.PENDIENTE_CONCILIACION, TipoVerificacion.CONCILIACION);
            }
            if ("pending".equals(remoto.estado())) {
                return sinPago(idSuscripcion, EstadoCobro.SIN_COBRO_APROBADO, TipoVerificacion.SIN_COBRO);
            }
            if (!"authorized".equals(remoto.estado())) {
                return sinPago(idSuscripcion, EstadoCobro.PENDIENTE_CONCILIACION, TipoVerificacion.CONCILIACION);
            }

            contexto = contexto.conProximoRecargo(remoto.proximoPago());
            Set<String> pagosConsultados = new HashSet<>();
            boolean empate = false;
            for (var cuota : proveedor.buscarCuotas(contexto.idMercadoPago())) {
                if (cuota == null || !contexto.idMercadoPago().equals(cuota.idPreapproval())) {
                    return sinPago(idSuscripcion, EstadoCobro.PENDIENTE_CONCILIACION, TipoVerificacion.CONCILIACION);
                }
                if (cuota.idPago() == null) {
                    continue;
                }
                if (!pagosConsultados.add(cuota.idPago())) {
                    continue;
                }
                var pago = proveedor.consultarPago(cuota.idPago());
                if (pago == null || !cuota.idPago().equals(pago.id()) || pago.estado() == null) {
                    return sinPago(idSuscripcion, EstadoCobro.PENDIENTE_CONCILIACION, TipoVerificacion.CONCILIACION);
                }
                if (!"approved".equals(pago.estado())) {
                    if (!Set.of("rejected", "cancelled").contains(pago.estado())) {
                        // Una cuota aun incierta podria ser el primer cobro.
                        TipoVerificacion tipo = Set.of("pending", "in_process", "authorized", "in_mediation")
                                .contains(pago.estado()) ? TipoVerificacion.COBRO_PENDIENTE : TipoVerificacion.CONCILIACION;
                        return sinPago(idSuscripcion, EstadoCobro.NO_SE_PUDO_VERIFICAR, tipo);
                    }
                    continue;
                }
                if (pago.importe() == null || plan.importe().compareTo(pago.importe()) != 0
                        || !plan.moneda().equals(pago.moneda()) || pago.fechaAprobacion() == null
                        || pago.id().length() > 150) {
                    return sinPago(idSuscripcion, EstadoCobro.PENDIENTE_CONCILIACION, TipoVerificacion.CONCILIACION);
                }
                if (primero == null || pago.fechaAprobacion().isBefore(primero.fechaAprobacion())) {
                    primero = pago;
                    empate = false;
                } else if (pago.fechaAprobacion().equals(primero.fechaAprobacion())) {
                    empate = true;
                }
            }
            if (primero == null) {
                return sinPago(idSuscripcion, EstadoCobro.SIN_COBRO_APROBADO, TipoVerificacion.SIN_COBRO);
            }
            if (empate || !"months".equals(plan.tipoFrecuencia())) {
                return sinPago(idSuscripcion, EstadoCobro.PENDIENTE_CONCILIACION, TipoVerificacion.CONCILIACION);
            }
            vencimiento = primero.fechaAprobacion().atZone(ZONA_VIGENCIA)
                    .plusMonths(plan.frecuencia()).toInstant();
        } catch (RuntimeException ex) {
            logger.warn("No se pudo verificar el primer cobro de la suscripcion {} por {}",
                    idSuscripcion, ex.getClass().getSimpleName());
            MercadoPagoException fallo = ex instanceof MercadoPagoException error
                    ? error : new MercadoPagoException("No se pudo verificar el primer cobro", ex);
            return new VerificacionCobro(
                    ResultadoCobro.sinPago(idSuscripcion, EstadoCobro.NO_SE_PUDO_VERIFICAR),
                    TipoVerificacion.ERROR_PROVEEDOR, fallo);
        }

        try {
            ResultadoCobro resultado = transacciones.confirmar(usuarioId, contexto, primero, vencimiento);
            TipoVerificacion tipo = resultado.estado() == EstadoCobro.PENDIENTE_CONCILIACION
                    ? TipoVerificacion.CONCILIACION : TipoVerificacion.CONFIRMADA;
            return new VerificacionCobro(resultado, tipo, null);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // La transaccion ya revirtio. La restriccion unica resuelve carreras entre cuentas.
            return sinPago(idSuscripcion, EstadoCobro.PENDIENTE_CONCILIACION, TipoVerificacion.CONCILIACION);
        }
    }

    private VerificacionCobro sinPago(UUID idSuscripcion, EstadoCobro estado, TipoVerificacion tipo) {
        return new VerificacionCobro(ResultadoCobro.sinPago(idSuscripcion, estado), tipo, null);
    }

    public enum TipoVerificacion {
        CONFIRMADA, SIN_COBRO, COBRO_PENDIENTE, CONCILIACION, ERROR_PROVEEDOR
    }

    public record VerificacionCobro(ResultadoCobro resultado, TipoVerificacion tipo, MercadoPagoException error) {
    }

}
