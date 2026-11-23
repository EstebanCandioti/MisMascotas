package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.MisMascotas.backend.Repository.*;
import com.MisMascotas.backend.Repository.NotificacionMercadoPagoRepository.*;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.*;
import com.MisMascotas.backend.Service.PlanificadorNotificacionMercadoPago.EstadoPrevio;

@ExtendWith(MockitoExtension.class)
class ProcesadorNotificacionesMercadoPagoProgramadoTests {
    static final Instant AHORA = Instant.parse("2026-11-18T16:00:00Z");
    final NotificacionMercadoPago primera = new NotificacionMercadoPago("n1", "subscription_authorized_payment", "f1", null);
    final NotificacionMercadoPago segunda = new NotificacionMercadoPago("n2", primera.topico(), "f2", null);
    @Mock NotificacionMercadoPagoRepository repo;
    @Mock ProcesadorNotificacionMercadoPagoService procesador;
    @Mock PlatformTransactionManager tx;
    @Mock SuscripcionRepository suscripciones;
    @Mock SuscripcionVencimientoService vencimientos;
    ProcesadorNotificacionesMercadoPagoProgramado scheduler;
    @BeforeEach void preparar() {
        scheduler = new ProcesadorNotificacionesMercadoPagoProgramado(repo, procesador, Clock.fixed(AHORA, ZoneOffset.UTC), tx, 20, suscripciones, vencimientos);
        lenient().when(tx.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        lenient().when(repo.estadoPrevio(any())).thenReturn(new EstadoPrevio(0,null,0,null));
        lenient().when(repo.aplicarResultado(any(),any())).thenReturn(true);
        lenient().when(repo.capturarPendientes(primera.topico(), "f1")).thenReturn(List.of(primera));
        lenient().when(repo.capturarPendientes(primera.topico(), "f2")).thenReturn(List.of(segunda));
    }
    @Test void guardaYConfirmaCadaRecursoAntesDeProcesarElSiguiente() {
        when(repo.buscarVencidas(AHORA,20)).thenReturn(List.of(primera,segunda));
        when(procesador.evaluar(any())).thenReturn(ResultadoProcesamiento.completada("ACTIVADA"));
        scheduler.ejecutar();
        var orden = inOrder(repo,procesador,tx,suscripciones);
        orden.verify(procesador).evaluar(primera); orden.verify(repo).aplicarResultado(eq("n1"), any());
        orden.verify(tx).commit(any()); orden.verify(procesador).evaluar(segunda);
        orden.verify(tx).commit(any()); orden.verify(suscripciones).buscarUsuariosConCoberturaVencida(AHORA,20);
    }
    @Test void agrupaDuplicadosDeFacturaYNoCapturaWebhooksLlegadosDuranteLaConsulta() {
        var duplicada = new NotificacionMercadoPago("n3", primera.topico(), "f1", null);
        when(repo.buscarVencidas(AHORA,20)).thenReturn(List.of(primera,duplicada));
        when(repo.capturarPendientes(primera.topico(),"f1")).thenReturn(List.of(primera,duplicada));
        when(procesador.evaluar(primera)).thenReturn(ResultadoProcesamiento.cobroPendiente("ESPERANDO_REINTENTO_MP"));
        scheduler.ejecutar();
        verify(procesador, times(1)).evaluar(any());
        verify(repo).aplicarResultado(eq("n1"), any()); verify(repo).aplicarResultado(eq("n3"), any());
        verify(repo, never()).aplicarResultado(eq("llegada-durante-http"),any());
    }
    @Test void inclusoEnElLimiteConsultaYReconoceLaAprobacion() {
        when(repo.buscarVencidas(AHORA,20)).thenReturn(List.of(primera));
        when(repo.estadoPrevio("n1")).thenReturn(new EstadoPrevio(0,null,10,AHORA.minusSeconds(864000)));
        when(procesador.evaluar(primera)).thenReturn(ResultadoProcesamiento.completada("RENOVADA"));
        scheduler.ejecutar();
        var c=ArgumentCaptor.forClass(ResultadoProgramado.class); verify(repo).aplicarResultado(eq("n1"),c.capture());
        assertThat(c.getValue().estado()).isEqualTo("COMPLETADA");
    }
    @Test void consultaFinalSinResolucionPasaARevision() {
        when(repo.buscarVencidas(AHORA,20)).thenReturn(List.of(primera));
        when(repo.estadoPrevio("n1")).thenReturn(new EstadoPrevio(0,null,10,AHORA.minusSeconds(864000)));
        when(procesador.evaluar(primera)).thenReturn(ResultadoProcesamiento.cobroPendiente("ESPERANDO_REINTENTO_MP"));
        scheduler.ejecutar();
        var c=ArgumentCaptor.forClass(ResultadoProgramado.class); verify(repo).aplicarResultado(eq("n1"),c.capture());
        assertThat(c.getValue().motivo()).isEqualTo("PLAZO_COBRO_PENDIENTE_AGOTADO");
        verify(procesador).evaluar(primera);
    }
    @Test void falloDeUnaEscrituraRevierteElGrupoYPermiteLaSiguienteFactura() {
        when(repo.buscarVencidas(AHORA,20)).thenReturn(List.of(primera,segunda));
        when(procesador.evaluar(any())).thenReturn(ResultadoProcesamiento.completada("YA_CONFIRMADA"));
        when(repo.aplicarResultado(eq("n1"),any())).thenThrow(new DataAccessResourceFailureException("QA"));
        scheduler.ejecutar(); verify(tx).rollback(any()); verify(repo).aplicarResultado(eq("n2"),any());
    }
    @Test void excepcionDelProcesadorProgramaReintentoTecnico() {
        when(repo.buscarVencidas(AHORA,20)).thenReturn(List.of(primera));
        when(procesador.evaluar(primera)).thenThrow(new IllegalStateException("QA"));
        scheduler.ejecutar();
        var c=ArgumentCaptor.forClass(ResultadoProgramado.class); verify(repo).aplicarResultado(eq("n1"),c.capture());
        assertThat(c.getValue().proximo()).isEqualTo(AHORA.plusSeconds(60));
    }
    @Test void persistenciaDeObservacionFallaDentroDeLaMismaTransaccionDelResultado() {
        when(repo.buscarVencidas(AHORA,20)).thenReturn(List.of(primera));
        var o=new Observacion("recycling","rejected","rechazo","p",1,AHORA,null);
        when(procesador.evaluar(primera)).thenReturn(ResultadoProcesamiento.cobroPendiente("ESPERANDO_REINTENTO_MP").conObservacion(o));
        doThrow(new DataAccessResourceFailureException("QA")).when(repo).guardarObservacion("n1",o);
        scheduler.ejecutar(); verify(tx).rollback(any()); verify(tx,never()).commit(any());
    }
    @Test void procesaVencimientosInclusoSiLaBandejaNoSePuedeConsultar() {
        when(repo.buscarVencidas(AHORA,20)).thenThrow(new DataAccessResourceFailureException("QA"));
        UUID u=UUID.randomUUID(); when(suscripciones.buscarUsuariosConCoberturaVencida(AHORA,20)).thenReturn(List.of(u));
        scheduler.ejecutar(); verify(vencimientos).vencer(u);
    }
}
