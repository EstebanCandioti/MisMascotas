package com.MisMascotas.backend.Service;

import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.MisMascotas.backend.Entity.Suscripcion;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.*;
import com.MisMascotas.backend.Repository.SuscripcionRepository;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.ResultadoProcesamiento;
import lombok.RequiredArgsConstructor;

/** Consulta al proveedor sin mantener una transaccion de escritura. */
@Service
@RequiredArgsConstructor
public class SuscripcionCicloService {
    private final MercadoPagoGateway proveedor;
    private final CatalogoPlanesSuscripcion catalogo;
    private final SuscripcionRepository suscripciones;
    private final SuscripcionCicloTransaccionalService transacciones;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResultadoProcesamiento registrar(Suscripcion local, FacturaAutorizada factura, PagoConsultado pago) {
        if (factura == null || pago == null || !"processed".equals(factura.estado())
                || !"approved".equals(pago.estado()) || !java.util.Objects.equals(factura.idPago(), pago.id())
                || !local.getIdSuscripcion().toString().equals(factura.referenciaExterna())
                || !java.util.Objects.equals(local.getIdSuscripcionMercadopago(), factura.idPreapproval())) {
            return ResultadoProcesamiento.revision("DATOS_COBRO_INCONSISTENTES");
        }
        var remoto = proveedor.consultarPreapproval(factura.idPreapproval());
        var plan = resolverPlan(local.getTipoPlan());
        if (!coincide(local, remoto, plan) || remoto.estado() == null || !Set.of("authorized", "paused", "cancelled", "canceled").contains(remoto.estado())
                || !"months".equals(plan.tipoFrecuencia()) || factura.importe() == null
                || plan.importe().compareTo(factura.importe()) != 0 || !plan.moneda().equals(factura.moneda())
                || pago.importe() == null || plan.importe().compareTo(pago.importe()) != 0
                || !plan.moneda().equals(pago.moneda()) || pago.fechaAprobacion() == null
                || pago.id().length() > 150 || factura.id().length() > 150) {
            return ResultadoProcesamiento.revision("DATOS_COBRO_INCONSISTENTES");
        }
        return transacciones.registrar(local.getUsuario().getIdUsuario(), local.getIdSuscripcion(),
                local.getTipoPlan(), factura, pago, remoto, plan.frecuencia());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResultadoProcesamiento sincronizar(String idRemoto) {
        var remoto = proveedor.consultarPreapproval(idRemoto);
        var local = suscripciones.findByIdSuscripcionMercadopagoConUsuario(idRemoto).orElse(null);
        if (local == null || remoto == null || !idRemoto.equals(remoto.id()))
            return ResultadoProcesamiento.revision("SUSCRIPCION_INCONSISTENTE");
        var plan = resolverPlan(local.getTipoPlan());
        if (!coincide(local, remoto, plan)) return ResultadoProcesamiento.revision("CONDICIONES_MODIFICADAS");
        if (remoto.estado() == null || !Set.of("pending", "authorized", "paused", "cancelled", "canceled").contains(remoto.estado()))
            return ResultadoProcesamiento.revision("ESTADO_SUSCRIPCION_NO_SOPORTADO");
        return transacciones.sincronizar(local.getUsuario().getIdUsuario(), local.getIdSuscripcion(),
                local.getTipoPlan(), remoto);
    }

    private PlanSuscripcion resolverPlan(String tipo) {
        try {
            return catalogo.resolver(tipo);
        } catch (com.MisMascotas.backend.Exception.ValidacionRequestException | IllegalStateException ex) {
            throw new com.MisMascotas.backend.Exception.MercadoPagoException("El catalogo de planes requiere revision");
        }
    }

    private boolean coincide(Suscripcion local, PreapprovalConsultado remoto, PlanSuscripcion plan) {
        return remoto != null && local.getIdSuscripcionMercadopago().equals(remoto.id())
                && local.getIdSuscripcion().toString().equals(remoto.referenciaExterna())
                && remoto.importe() != null && plan.importe().compareTo(remoto.importe()) == 0
                && plan.moneda().equals(remoto.moneda()) && plan.frecuencia() == remoto.frecuencia()
                && plan.tipoFrecuencia().equals(remoto.tipoFrecuencia());
    }
}
