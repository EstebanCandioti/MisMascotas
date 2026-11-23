package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.MisMascotas.backend.Exception.ValidacionRequestException;

class CatalogoPlanesSuscripcionTests {

    @Test
    void resuelveImporteMonedaYFrecuenciaDelLadoDelServidor() {
        var catalogo = new CatalogoPlanesSuscripcion(
                "2500.00",
                "ARS",
                1,
                "months",
                "Plan Premium MisMascotas");

        PlanSuscripcion plan = catalogo.resolver(CatalogoPlanesSuscripcion.PREMIUM_MENSUAL);

        assertThat(plan.importe()).isEqualByComparingTo(new BigDecimal("2500.00"));
        assertThat(plan.moneda()).isEqualTo("ARS");
        assertThat(plan.frecuencia()).isEqualTo(1);
        assertThat(plan.tipoFrecuencia()).isEqualTo("months");
    }

    @Test
    void rechazaPlanQueNoPerteneceAlCatalogo() {
        var catalogo = new CatalogoPlanesSuscripcion(
                "2500.00",
                "ARS",
                1,
                "months",
                "Plan Premium MisMascotas");

        assertThatThrownBy(() -> catalogo.resolver("PLAN_INVENTADO"))
                .isInstanceOf(ValidacionRequestException.class);
    }

    @Test
    void noReservaConImporteSinConfigurar() {
        var catalogo = new CatalogoPlanesSuscripcion(
                "",
                "ARS",
                1,
                "months",
                "Plan Premium MisMascotas");

        assertThatThrownBy(() -> catalogo.resolver(CatalogoPlanesSuscripcion.PREMIUM_MENSUAL))
                .isInstanceOf(IllegalStateException.class);
    }
}
