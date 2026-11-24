package com.MisMascotas.backend.Service;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.MisMascotas.backend.Exception.ValidacionRequestException;

@Component
public class CatalogoPlanesSuscripcion {

    public static final String PREMIUM_MENSUAL = "PREMIUM_MENSUAL";

    private final String importePremiumMensual;
    private final String monedaPremiumMensual;
    private final int frecuenciaPremiumMensual;
    private final String tipoFrecuenciaPremiumMensual;
    private final String motivoPremiumMensual;

    public CatalogoPlanesSuscripcion(
            @Value("${suscripciones.planes.premium-mensual.importe:}") String importePremiumMensual,
            @Value("${suscripciones.planes.premium-mensual.moneda:ARS}") String monedaPremiumMensual,
            @Value("${suscripciones.planes.premium-mensual.frecuencia:1}") int frecuenciaPremiumMensual,
            @Value("${suscripciones.planes.premium-mensual.frecuencia-tipo:months}") String tipoFrecuenciaPremiumMensual,
            @Value("${suscripciones.planes.premium-mensual.motivo:Plan Premium MisMascotas}") String motivoPremiumMensual) {
        this.importePremiumMensual = importePremiumMensual;
        this.monedaPremiumMensual = monedaPremiumMensual;
        this.frecuenciaPremiumMensual = frecuenciaPremiumMensual;
        this.tipoFrecuenciaPremiumMensual = tipoFrecuenciaPremiumMensual;
        this.motivoPremiumMensual = motivoPremiumMensual;
    }

    public PlanSuscripcion resolver(String tipoPlan) {
        if (!PREMIUM_MENSUAL.equals(tipoPlan)) {
            throw new ValidacionRequestException("tipoPlan: El plan solicitado no es valido");
        }

        BigDecimal importe = parsearImporte(importePremiumMensual);
        if (importe.signum() <= 0
                || monedaPremiumMensual == null
                || monedaPremiumMensual.isBlank()
                || frecuenciaPremiumMensual <= 0
                || tipoFrecuenciaPremiumMensual == null
                || tipoFrecuenciaPremiumMensual.isBlank()
                || motivoPremiumMensual == null
                || motivoPremiumMensual.isBlank()) {
            throw new IllegalStateException("El plan Premium mensual no esta configurado correctamente");
        }

        return new PlanSuscripcion(
                PREMIUM_MENSUAL,
                motivoPremiumMensual,
                importe,
                monedaPremiumMensual,
                frecuenciaPremiumMensual,
                tipoFrecuenciaPremiumMensual);
    }

    private BigDecimal parsearImporte(String importe) {
        if (importe == null || importe.isBlank()) {
            throw new IllegalStateException("El importe del plan Premium mensual no esta configurado");
        }

        try {
            return new BigDecimal(importe);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("El importe del plan Premium mensual no es valido", ex);
        }
    }
}
