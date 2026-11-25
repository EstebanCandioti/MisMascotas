package com.MisMascotas.backend.Service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PreapprovalConsultado;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PreapprovalCreado;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.SolicitudPreapproval;
import com.MisMascotas.backend.Service.SuscripcionReservaService.ReservaParaContinuar;
import com.MisMascotas.backend.Service.SuscripcionReservaService.ReservaSuscripcion;
import com.MisMascotas.backend.Service.SuscripcionReservaService.ResultadoReservaReemplazo;

@Service
public class SuscripcionService {

    private static final Logger logger = LoggerFactory.getLogger(SuscripcionService.class);

    private final CatalogoPlanesSuscripcion catalogoPlanes;
    private final SuscripcionReservaService reservaService;
    private final MercadoPagoGateway mercadoPagoGateway;
    private final SuscripcionPrimerCobroService primerCobroService;

    public SuscripcionService(
            CatalogoPlanesSuscripcion catalogoPlanes,
            SuscripcionReservaService reservaService,
            MercadoPagoGateway mercadoPagoGateway,
            SuscripcionPrimerCobroService primerCobroService) {
        this.catalogoPlanes = catalogoPlanes;
        this.reservaService = reservaService;
        this.mercadoPagoGateway = mercadoPagoGateway;
        this.primerCobroService = primerCobroService;
    }

    public SuscripcionCobroTransaccionalService.ResultadoCobro confirmarPrimerCobro(UUID usuarioId, UUID idSuscripcion) {
        return primerCobroService.confirmar(usuarioId, idSuscripcion);
    }

    public java.util.List<PlanSuscripcion> listarPlanes() {
        return catalogoPlanes.listar();
    }

    public SuscripcionPrimerCobroService.VerificacionCobro confirmarPrimerCobroConDetalle(
            UUID usuarioId, UUID idSuscripcion) {
        return primerCobroService.confirmarConDetalle(usuarioId, idSuscripcion);
    }

    public ResultadoAltaSuscripcion iniciarAlta(UUID usuarioId, String tipoPlan) {
        PlanSuscripcion plan = catalogoPlanes.resolver(tipoPlan);
        mercadoPagoGateway.validarConfiguracion();

        ReservaSuscripcion reserva = reservaService.reservar(usuarioId, plan.codigo());
        if (!reserva.nueva()) {
            EstadoAltaSuscripcion estado = reserva.idMercadoPago() == null
                    ? EstadoAltaSuscripcion.PENDIENTE_CONCILIACION
                    : EstadoAltaSuscripcion.INTENTO_EN_CURSO;
            return new ResultadoAltaSuscripcion(reserva.idSuscripcion(), estado, null);
        }

        ResultadoCreacionRemota creacion = crearPreapproval(plan, reserva);
        if (creacion.confirmada()) {
            return new ResultadoAltaSuscripcion(
                    reserva.idSuscripcion(),
                    EstadoAltaSuscripcion.CHECKOUT_DISPONIBLE,
                    creacion.initPoint());
        }
        return new ResultadoAltaSuscripcion(
                reserva.idSuscripcion(),
                EstadoAltaSuscripcion.PENDIENTE_CONCILIACION,
                null);
    }

    public ResultadoContinuacionSuscripcion continuarAlta(UUID usuarioId, UUID idSuscripcion) {
        ReservaParaContinuar reserva = reservaService.buscarParaContinuar(usuarioId, idSuscripcion);
        if (!reserva.pendiente()) {
            return resultadoContinuacion(reserva.idSuscripcion(), EstadoContinuacionSuscripcion.NO_CONTINUABLE);
        }
        if (reserva.idMercadoPago() == null || reserva.idMercadoPago().isBlank()) {
            return resultadoContinuacion(
                    reserva.idSuscripcion(),
                    EstadoContinuacionSuscripcion.PENDIENTE_CONCILIACION);
        }

        PlanSuscripcion plan;
        try {
            plan = catalogoPlanes.resolver(reserva.tipoPlan());
            PreapprovalConsultado preapproval = mercadoPagoGateway
                    .consultarPreapproval(reserva.idMercadoPago());
            return evaluarContinuacion(reserva, plan, preapproval);
        } catch (RuntimeException ex) {
            logger.warn(
                    "No se pudo confirmar la continuacion de la suscripcion {} por {}",
                    reserva.idSuscripcion(),
                    ex.getClass().getSimpleName());
            return resultadoContinuacion(
                    reserva.idSuscripcion(),
                    EstadoContinuacionSuscripcion.NO_SE_PUDO_CONFIRMAR);
        }
    }

    private ResultadoContinuacionSuscripcion evaluarContinuacion(
            ReservaParaContinuar reserva,
            PlanSuscripcion plan,
            PreapprovalConsultado preapproval) {
        if (!reserva.idMercadoPago().equals(preapproval.id())
                || !reserva.idSuscripcion().toString().equals(preapproval.referenciaExterna())) {
            return resultadoContinuacion(
                    reserva.idSuscripcion(),
                    EstadoContinuacionSuscripcion.NO_SE_PUDO_CONFIRMAR);
        }

        if ("authorized".equalsIgnoreCase(preapproval.estado())) {
            return resultadoContinuacion(
                    reserva.idSuscripcion(),
                    EstadoContinuacionSuscripcion.REQUIERE_VERIFICAR_PRIMER_COBRO);
        }
        if ("cancelled".equalsIgnoreCase(preapproval.estado())) {
            return resultadoContinuacion(
                    reserva.idSuscripcion(),
                    EstadoContinuacionSuscripcion.NO_CONTINUABLE);
        }
        if (!"pending".equalsIgnoreCase(preapproval.estado())) {
            return resultadoContinuacion(
                    reserva.idSuscripcion(),
                    EstadoContinuacionSuscripcion.NO_SE_PUDO_CONFIRMAR);
        }

        boolean condicionesVigentes = plan.importe().compareTo(preapproval.importe()) == 0
                && plan.moneda().equalsIgnoreCase(preapproval.moneda())
                && plan.frecuencia() == preapproval.frecuencia()
                && plan.tipoFrecuencia().equalsIgnoreCase(preapproval.tipoFrecuencia());
        if (!condicionesVigentes) {
            return resultadoContinuacion(
                    reserva.idSuscripcion(),
                    EstadoContinuacionSuscripcion.NO_CONTINUABLE);
        }

        return new ResultadoContinuacionSuscripcion(
                reserva.idSuscripcion(),
                EstadoContinuacionSuscripcion.CHECKOUT_DISPONIBLE,
                preapproval.initPoint());
    }

    private ResultadoContinuacionSuscripcion resultadoContinuacion(
            UUID idSuscripcion,
            EstadoContinuacionSuscripcion estado) {
        return new ResultadoContinuacionSuscripcion(idSuscripcion, estado, null);
    }

    public ResultadoReemplazoSuscripcion reemplazarAlta(UUID usuarioId, UUID idSuscripcion) {
        ReservaParaContinuar anterior = reservaService.buscarParaContinuar(usuarioId, idSuscripcion);
        if (!anterior.pendiente()) {
            return resultadoReemplazo(
                    anterior.idSuscripcion(),
                    null,
                    EstadoReemplazoSuscripcion.NO_REEMPLAZABLE);
        }
        if (anterior.idMercadoPago() == null || anterior.idMercadoPago().isBlank()) {
            return resultadoReemplazo(
                    anterior.idSuscripcion(),
                    null,
                    EstadoReemplazoSuscripcion.PENDIENTE_CONCILIACION);
        }

        PlanSuscripcion plan;
        PreapprovalConsultado consultaInicial;
        try {
            plan = catalogoPlanes.resolver(anterior.tipoPlan());
            mercadoPagoGateway.validarConfiguracion();
            consultaInicial = mercadoPagoGateway.consultarPreapproval(anterior.idMercadoPago());
        } catch (RuntimeException ex) {
            registrarCancelacionNoConfirmada(anterior.idSuscripcion(), ex);
            return cancelacionNoConfirmada(anterior.idSuscripcion());
        }

        if (!identidadRemotaCoincide(anterior, consultaInicial)) {
            return cancelacionNoConfirmada(anterior.idSuscripcion());
        }
        if ("authorized".equalsIgnoreCase(consultaInicial.estado())) {
            return resultadoReemplazo(
                    anterior.idSuscripcion(),
                    null,
                    EstadoReemplazoSuscripcion.REQUIERE_VERIFICAR_PRIMER_COBRO);
        }

        if ("pending".equalsIgnoreCase(consultaInicial.estado())) {
            try {
                mercadoPagoGateway.cancelarPreapproval(anterior.idMercadoPago());
            } catch (RuntimeException ex) {
                logger.warn(
                        "No se obtuvo respuesta al cancelar la suscripcion {} por {}. Se verificara su estado",
                        anterior.idSuscripcion(),
                        ex.getClass().getSimpleName());
            }

            PreapprovalConsultado consultaPosterior;
            try {
                consultaPosterior = mercadoPagoGateway.consultarPreapproval(anterior.idMercadoPago());
            } catch (RuntimeException ex) {
                registrarCancelacionNoConfirmada(anterior.idSuscripcion(), ex);
                return cancelacionNoConfirmada(anterior.idSuscripcion());
            }

            if (!identidadRemotaCoincide(anterior, consultaPosterior)) {
                return cancelacionNoConfirmada(anterior.idSuscripcion());
            }
            if ("authorized".equalsIgnoreCase(consultaPosterior.estado())) {
                return resultadoReemplazo(
                        anterior.idSuscripcion(),
                        null,
                        EstadoReemplazoSuscripcion.REQUIERE_VERIFICAR_PRIMER_COBRO);
            }
            if (!"cancelled".equalsIgnoreCase(consultaPosterior.estado())) {
                return cancelacionNoConfirmada(anterior.idSuscripcion());
            }
        } else if (!"cancelled".equalsIgnoreCase(consultaInicial.estado())) {
            return cancelacionNoConfirmada(anterior.idSuscripcion());
        }

        ResultadoReservaReemplazo reemplazo;
        try {
            reemplazo = reservaService.reemplazarTrasCancelacionConfirmada(
                    usuarioId,
                    anterior.idSuscripcion(),
                    anterior.idMercadoPago());
        } catch (RuntimeException ex) {
            logger.warn(
                    "La cancelacion remota de la suscripcion {} fue confirmada, pero fallo la reserva local por {}",
                    anterior.idSuscripcion(),
                    ex.getClass().getSimpleName());
            return resultadoReemplazo(
                    anterior.idSuscripcion(),
                    null,
                    EstadoReemplazoSuscripcion.PENDIENTE_CONCILIACION);
        }

        if (reemplazo.estado() == SuscripcionReservaService.EstadoReservaReemplazo.YA_PROCESADO) {
            return resultadoReemplazo(
                    anterior.idSuscripcion(),
                    null,
                    EstadoReemplazoSuscripcion.NO_REEMPLAZABLE);
        }
        if (reemplazo.estado() == SuscripcionReservaService.EstadoReservaReemplazo.INTENTO_PENDIENTE) {
            return resultadoReemplazo(
                    anterior.idSuscripcion(),
                    reemplazo.reserva().idSuscripcion(),
                    EstadoReemplazoSuscripcion.INTENTO_EN_CURSO);
        }

        ReservaSuscripcion nueva = reemplazo.reserva();
        ResultadoCreacionRemota creacion = crearPreapproval(plan, nueva);
        if (!creacion.confirmada()) {
            return resultadoReemplazo(
                    anterior.idSuscripcion(),
                    nueva.idSuscripcion(),
                    EstadoReemplazoSuscripcion.PENDIENTE_CONCILIACION);
        }
        return new ResultadoReemplazoSuscripcion(
                anterior.idSuscripcion(),
                nueva.idSuscripcion(),
                EstadoReemplazoSuscripcion.CHECKOUT_DISPONIBLE,
                creacion.initPoint());
    }

    private ResultadoCreacionRemota crearPreapproval(
            PlanSuscripcion plan,
            ReservaSuscripcion reserva) {
        try {
            PreapprovalCreado preapproval = mercadoPagoGateway.crearPreapproval(
                    new SolicitudPreapproval(
                            plan.motivo(),
                            reserva.idSuscripcion().toString(),
                            reserva.emailUsuario(),
                            plan.importe(),
                            plan.moneda(),
                            plan.frecuencia(),
                            plan.tipoFrecuencia()));
            reservaService.confirmarIdMercadoPago(reserva.idSuscripcion(), preapproval.id());
            return new ResultadoCreacionRemota(true, preapproval.initPoint());
        } catch (RuntimeException ex) {
            logger.warn(
                    "La suscripcion {} quedo pendiente de conciliacion por {}",
                    reserva.idSuscripcion(),
                    ex.getClass().getSimpleName());
            return new ResultadoCreacionRemota(false, null);
        }
    }

    private boolean identidadRemotaCoincide(
            ReservaParaContinuar reserva,
            PreapprovalConsultado preapproval) {
        return reserva.idMercadoPago().equals(preapproval.id())
                && reserva.idSuscripcion().toString().equals(preapproval.referenciaExterna());
    }

    private void registrarCancelacionNoConfirmada(UUID suscripcionId, RuntimeException ex) {
        logger.warn(
                "No se pudo confirmar la cancelacion de la suscripcion {} por {}",
                suscripcionId,
                ex.getClass().getSimpleName());
    }

    private ResultadoReemplazoSuscripcion cancelacionNoConfirmada(UUID idSuscripcion) {
        return resultadoReemplazo(
                idSuscripcion,
                null,
                EstadoReemplazoSuscripcion.CANCELACION_NO_CONFIRMADA);
    }

    private ResultadoReemplazoSuscripcion resultadoReemplazo(
            UUID idAnterior,
            UUID idNueva,
            EstadoReemplazoSuscripcion estado) {
        return new ResultadoReemplazoSuscripcion(idAnterior, idNueva, estado, null);
    }

    public enum EstadoAltaSuscripcion {
        CHECKOUT_DISPONIBLE,
        INTENTO_EN_CURSO,
        PENDIENTE_CONCILIACION
    }

    public record ResultadoAltaSuscripcion(
            UUID idSuscripcion,
            EstadoAltaSuscripcion estado,
            String initPoint) {
    }

    public enum EstadoContinuacionSuscripcion {
        CHECKOUT_DISPONIBLE,
        PENDIENTE_CONCILIACION,
        REQUIERE_VERIFICAR_PRIMER_COBRO,
        NO_CONTINUABLE,
        NO_SE_PUDO_CONFIRMAR
    }

    public record ResultadoContinuacionSuscripcion(
            UUID idSuscripcion,
            EstadoContinuacionSuscripcion estado,
            String initPoint) {
    }

    public enum EstadoReemplazoSuscripcion {
        CHECKOUT_DISPONIBLE,
        PENDIENTE_CONCILIACION,
        REQUIERE_VERIFICAR_PRIMER_COBRO,
        CANCELACION_NO_CONFIRMADA,
        INTENTO_EN_CURSO,
        NO_REEMPLAZABLE
    }

    public record ResultadoReemplazoSuscripcion(
            UUID idSuscripcionAnterior,
            UUID idSuscripcionNueva,
            EstadoReemplazoSuscripcion estado,
            String initPoint) {
    }

    private record ResultadoCreacionRemota(boolean confirmada, String initPoint) {
    }
}
