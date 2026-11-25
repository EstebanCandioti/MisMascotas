package com.MisMascotas.backend.Service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.MisMascotas.backend.Exception.ValidacionRequestException;

@Component
public class CatalogoPlanesSuscripcion {

    public static final String PREMIUM_MENSUAL = "PREMIUM_MENSUAL";
    public static final String PREMIUM_ANUAL = "PREMIUM_ANUAL";

    private final String importePremiumMensual;
    private final String monedaPremiumMensual;
    private final int frecuenciaPremiumMensual;
    private final String tipoFrecuenciaPremiumMensual;
    private final String motivoPremiumMensual;
    private final String importePremiumAnual;
    private final String monedaPremiumAnual;
    private final int frecuenciaPremiumAnual;
    private final String tipoFrecuenciaPremiumAnual;
    private final String motivoPremiumAnual;

    public CatalogoPlanesSuscripcion(
            @Value("${suscripciones.planes.premium-mensual.importe:4000}") String importePremiumMensual,
            @Value("${suscripciones.planes.premium-mensual.moneda:ARS}") String monedaPremiumMensual,
            @Value("${suscripciones.planes.premium-mensual.frecuencia:1}") int frecuenciaPremiumMensual,
            @Value("${suscripciones.planes.premium-mensual.frecuencia-tipo:months}") String tipoFrecuenciaPremiumMensual,
            @Value("${suscripciones.planes.premium-mensual.motivo:Plan Premium MisMascotas}") String motivoPremiumMensual,
            @Value("${suscripciones.planes.premium-anual.importe:40000}") String importePremiumAnual,
            @Value("${suscripciones.planes.premium-anual.moneda:ARS}") String monedaPremiumAnual,
            @Value("${suscripciones.planes.premium-anual.frecuencia:12}") int frecuenciaPremiumAnual,
            @Value("${suscripciones.planes.premium-anual.frecuencia-tipo:months}") String tipoFrecuenciaPremiumAnual,
            @Value("${suscripciones.planes.premium-anual.motivo:Plan Premium anual MisMascotas}") String motivoPremiumAnual) {
        this.importePremiumMensual = importePremiumMensual;
        this.monedaPremiumMensual = monedaPremiumMensual;
        this.frecuenciaPremiumMensual = frecuenciaPremiumMensual;
        this.tipoFrecuenciaPremiumMensual = tipoFrecuenciaPremiumMensual;
        this.motivoPremiumMensual = motivoPremiumMensual;
        this.importePremiumAnual = importePremiumAnual;
        this.monedaPremiumAnual = monedaPremiumAnual;
        this.frecuenciaPremiumAnual = frecuenciaPremiumAnual;
        this.tipoFrecuenciaPremiumAnual = tipoFrecuenciaPremiumAnual;
        this.motivoPremiumAnual = motivoPremiumAnual;
    }

    public PlanSuscripcion resolver(String tipoPlan) {
        if (PREMIUM_MENSUAL.equals(tipoPlan))
            return validar(PREMIUM_MENSUAL, motivoPremiumMensual, importePremiumMensual,
                    monedaPremiumMensual, frecuenciaPremiumMensual, tipoFrecuenciaPremiumMensual, 1);
        if (PREMIUM_ANUAL.equals(tipoPlan))
            return validar(PREMIUM_ANUAL, motivoPremiumAnual, importePremiumAnual,
                    monedaPremiumAnual, frecuenciaPremiumAnual, tipoFrecuenciaPremiumAnual, 12);
        throw new ValidacionRequestException("tipoPlan: El plan solicitado no es valido");
    }

    public List<PlanSuscripcion> listar() {
        return List.of(resolver(PREMIUM_MENSUAL), resolver(PREMIUM_ANUAL));
    }

    private PlanSuscripcion validar(String codigo, String motivo, String textoImporte, String moneda,
            int frecuencia, String unidad, int mesesEsperados) {
        BigDecimal importe = parsearImporte(textoImporte);
        if (importe.signum() <= 0 || !"ARS".equals(moneda) || frecuencia != mesesEsperados
                || !"months".equals(unidad) || motivo == null || motivo.isBlank()) {
            throw new IllegalStateException("El plan " + codigo + " no esta configurado correctamente");
        }
        return new PlanSuscripcion(codigo, motivo, importe, moneda, frecuencia, unidad);
    }

    private BigDecimal parsearImporte(String importe) {
        if (importe == null || importe.isBlank()) {
            throw new IllegalStateException("El importe del plan Premium no esta configurado");
        }

        try {
            return new BigDecimal(importe);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("El importe del plan Premium no es valido", ex);
        }
    }
}
