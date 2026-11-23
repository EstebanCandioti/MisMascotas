package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.MisMascotas.backend.Service.PlanificadorNotificacionMercadoPago.EstadoPrevio;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.ResultadoProcesamiento;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.Estado;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.TipoReintento;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PlanificadorNotificacionMercadoPagoTests {

    private static final Instant AHORA = Instant.parse("2026-11-18T16:00:00Z");
    private static final EstadoPrevio SIN_INTENTOS = new EstadoPrevio(0, null, 0, null);

    @Test
    void temporalUsaLaSecuenciaYConservaElHistorialDeCobroPendiente() {
        long[] segundos = {60, 300, 900, 3600, 3600};
        for (int intento = 0; intento < segundos.length; intento++) {
            var previo = new EstadoPrevio(intento, AHORA.minusSeconds(120), 2, AHORA.minusSeconds(600));
            var resultado = PlanificadorNotificacionMercadoPago.planificar(ResultadoProcesamiento.temporal("FALLO_TEMPORAL"), AHORA, previo);
            assertThat(resultado.proximo()).isEqualTo(AHORA.plusSeconds(segundos[intento]));
            assertThat(resultado.intentosTemporales()).isEqualTo(intento + 1);
            assertThat(resultado.primerTemporal()).isEqualTo(previo.primerTemporal());
            assertThat(resultado.intentosPendientes()).isEqualTo(2);
            assertThat(resultado.primerPendiente()).isEqualTo(previo.primerPendiente());
        }
    }

    @Test
    void resolucionSiempreEsCadaCincoMinutosYRecuperaElEpisodioTecnico() {
        for (int intento = 0; intento < 5; intento++) {
            var previo = new EstadoPrevio(4, AHORA.minusSeconds(86400), intento, AHORA.minusSeconds(3600));
            var plan = PlanificadorNotificacionMercadoPago.planificar(ResultadoProcesamiento.cobroPendiente("ESPERANDO_RESOLUCION_MP"), AHORA, previo);
            assertThat(plan.proximo()).isEqualTo(AHORA.plusSeconds(300));
            assertThat(plan.primerTemporal()).isNull(); assertThat(plan.intentosTemporales()).isZero();
            assertThat(plan.primerPendiente()).isEqualTo(previo.primerPendiente());
        }
    }
    @Test void recyclingEsDiarioYNoDependeDelContadorMP() {
        var plan = PlanificadorNotificacionMercadoPago.planificar(ResultadoProcesamiento.cobroPendiente("ESPERANDO_REINTENTO_MP"), AHORA, SIN_INTENTOS);
        assertThat(plan.proximo()).isEqualTo(AHORA.plusSeconds(86400));
    }

    @Test
    void finalizaYEliminaLaProximaEjecucion() {
        var resultado = PlanificadorNotificacionMercadoPago.planificar(ResultadoProcesamiento.completada("YA_CONFIRMADA"), AHORA, SIN_INTENTOS);
        assertThat(resultado.estado()).isEqualTo("COMPLETADA");
        assertThat(resultado.procesadaEn()).isEqualTo(AHORA);
        assertThat(resultado.proximo()).isNull();
    }

    @Test
    void conciliacionRequiereRevision() {
        var resultado = PlanificadorNotificacionMercadoPago.planificar(
                ResultadoProcesamiento.revision("COBRO_PENDIENTE_CONCILIACION"), AHORA, SIN_INTENTOS);
        assertThat(resultado.estado()).isEqualTo("REVISION");
        assertThat(resultado.procesadaEn()).isNull();
        assertThat(resultado.proximo()).isNull();
    }

    @Test
    void errorDeVerificacionSeReintentaComoFalloTemporal() {
        var resultado = PlanificadorNotificacionMercadoPago.planificar(
                ResultadoProcesamiento.temporal("COBRO_NO_SE_PUDO_VERIFICAR"), AHORA, SIN_INTENTOS);
        assertThat(resultado.proximo()).isEqualTo(AHORA.plusSeconds(60));
        assertThat(resultado.intentosTemporales()).isEqualTo(1);
        assertThat(resultado.intentosPendientes()).isZero();
    }

    @Test void elPlazoDeNegocioNoSeReiniciaAlCambiarDeEstado() {
        var previo = new EstadoPrevio(0, null, 30, AHORA.minusSeconds(864000));
        for (String motivo : new String[]{"ESPERANDO_REINTENTO_MP", "ESPERANDO_RESOLUCION_MP"}) {
            assertThat(PlanificadorNotificacionMercadoPago.planificar(ResultadoProcesamiento.cobroPendiente(motivo), AHORA, previo).motivo())
                    .isEqualTo("PLAZO_COBRO_PENDIENTE_AGOTADO");
        }
        assertThat(PlanificadorNotificacionMercadoPago.planificar(ResultadoProcesamiento.completada("ACTIVADA"), AHORA, previo).estado())
                .isEqualTo("COMPLETADA");
    }
    @Test void ultimaComprobacionQuedaEnElLimiteDeDiezDias() {
        var previo = new EstadoPrevio(0, null, 9, AHORA.minusSeconds(864000 - 60));
        assertThat(PlanificadorNotificacionMercadoPago.planificar(ResultadoProcesamiento.cobroPendiente("ESPERANDO_REINTENTO_MP"), AHORA, previo).proximo())
                .isEqualTo(AHORA.plusSeconds(60));
    }

    @Test
    void programaLaRevisionAlCumplirElPlazoSinSobrepasarlo() {
        var previo = new EstadoPrevio(4, AHORA.minusSeconds(86390), 0, null);
        var resultado = PlanificadorNotificacionMercadoPago.planificar(ResultadoProcesamiento.temporal("FALLO_TEMPORAL"), AHORA, previo);
        assertThat(resultado.proximo()).isEqualTo(AHORA.plusSeconds(10));
        assertThat(PlanificadorNotificacionMercadoPago.planificar(ResultadoProcesamiento.temporal("FALLO_TEMPORAL"), resultado.proximo(), previo).estado())
                .isEqualTo("REVISION");
    }
    @ParameterizedTest
    @ValueSource(strings = {"120", "Wed, 18 Nov 2026 16:02:00 GMT"})
    void respetaLasDosFormasDeRetryAfter(String retryAfter) {
        var resultado = new ResultadoProcesamiento(Estado.PENDIENTE, "HTTP_429", TipoReintento.TEMPORAL, retryAfter);
        assertThat(PlanificadorNotificacionMercadoPago.planificar(resultado, AHORA, SIN_INTENTOS).proximo())
                .isEqualTo(AHORA.plusSeconds(120));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "invalido", "-1", "1.5", "+120"})
    void retryAfterAusenteOInvalidoUsaLaSecuenciaTemporal(String retryAfter) {
        var resultado = new ResultadoProcesamiento(Estado.PENDIENTE, "HTTP_429", TipoReintento.TEMPORAL, retryAfter);
        var plan = PlanificadorNotificacionMercadoPago.planificar(resultado, AHORA, SIN_INTENTOS);
        assertThat(plan.proximo()).isEqualTo(AHORA.plusSeconds(60));
        assertThat(plan.intentosTemporales()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"86400", "90000", "999999999999999999999999999999999999", "Thu, 19 Nov 2026 17:00:00 GMT"})
    void retryAfterFueraDelPlazoQuedaParaRevision(String retryAfter) {
        var resultado = new ResultadoProcesamiento(Estado.PENDIENTE, "HTTP_429", TipoReintento.TEMPORAL, retryAfter);
        var plan = PlanificadorNotificacionMercadoPago.planificar(resultado, AHORA, SIN_INTENTOS);
        assertThat(plan.estado()).isEqualTo("REVISION");
        assertThat(plan.motivo()).isEqualTo("RETRY_AFTER_FUERA_DE_PLAZO");
        assertThat(plan.proximo()).isNull();
        assertThat(plan.intentosTemporales()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "Tue, 17 Nov 2026 16:00:00 GMT"})
    void retryAfterQueNoExigeEsperaQuedaDisponibleParaLaProximaTanda(String retryAfter) {
        var resultado = new ResultadoProcesamiento(Estado.PENDIENTE, "HTTP_429", TipoReintento.TEMPORAL, retryAfter);
        assertThat(PlanificadorNotificacionMercadoPago.planificar(resultado, AHORA, SIN_INTENTOS).proximo())
                .isEqualTo(AHORA);
    }

    @Test
    void retryAfterRespetaTambienElPlazoPrevioDeCobroPendiente() {
        var previo = new EstadoPrevio(0, null, 2, AHORA.minusSeconds(863990));
        var resultado = new ResultadoProcesamiento(Estado.PENDIENTE, "HTTP_429", TipoReintento.TEMPORAL, "120");
        assertThat(PlanificadorNotificacionMercadoPago.planificar(resultado, AHORA, previo).estado())
                .isEqualTo("REVISION");
    }

}
