package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.MisMascotas.backend.Exception.ValidacionRequestException;

class CatalogoPlanesSuscripcionTests {

    @Test void listaMensualYAnualConLosImportesAcordados() {
        var catalogo = catalogo("40000", "ARS", 12, "months");
        var planes = catalogo.listar();
        assertThat(planes).extracting(PlanSuscripcion::codigo)
                .containsExactly("PREMIUM_MENSUAL", "PREMIUM_ANUAL");
        assertThat(planes.get(0).importe()).isEqualByComparingTo("4000");
        assertThat(planes.get(1).importe()).isEqualByComparingTo("40000");
        assertThat(planes.get(1).frecuencia()).isEqualTo(12);
        assertThat(planes.get(1).tipoFrecuencia()).isEqualTo("months");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "0", "-1", "no-es-importe"})
    void noOfreceUnAnualConImporteInvalido(String importe) {
        assertThatThrownBy(() -> catalogo(importe, "ARS", 12, "months").listar())
                .isInstanceOf(IllegalStateException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"USD,12,months", "ARS,1,months", "ARS,12,days"})
    void noOfreceUnAnualConMonedaOPeriodicidadIncorrecta(String moneda, int frecuencia, String unidad) {
        assertThatThrownBy(() -> catalogo("40000", moneda, frecuencia, unidad).resolver("PREMIUM_ANUAL"))
                .isInstanceOf(IllegalStateException.class);
    }

    private CatalogoPlanesSuscripcion catalogo(String anual, String moneda, int frecuencia, String unidad) {
        return new CatalogoPlanesSuscripcion("4000", "ARS", 1, "months", "Premium mensual",
                anual, moneda, frecuencia, unidad, "Premium anual");
    }

    @Test
    void resuelveImporteMonedaYFrecuenciaDelLadoDelServidor() {
        var catalogo = new CatalogoPlanesSuscripcion(
                "2500.00",
                "ARS",
                1,
                "months",
                "Plan Premium MisMascotas", "40000", "ARS", 12, "months", "Plan Premium anual MisMascotas");

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
                "Plan Premium MisMascotas", "40000", "ARS", 12, "months", "Plan Premium anual MisMascotas");

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
                "Plan Premium MisMascotas", "40000", "ARS", 12, "months", "Plan Premium anual MisMascotas");

        assertThatThrownBy(() -> catalogo.resolver(CatalogoPlanesSuscripcion.PREMIUM_MENSUAL))
                .isInstanceOf(IllegalStateException.class);
    }
}
