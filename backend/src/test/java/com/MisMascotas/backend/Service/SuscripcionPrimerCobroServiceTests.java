package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.*;
import com.MisMascotas.backend.Service.SuscripcionCobroTransaccionalService.*;

@ExtendWith(MockitoExtension.class)
class SuscripcionPrimerCobroServiceTests {

    @Mock private SuscripcionCobroTransaccionalService transacciones;
    @Mock private MercadoPagoGateway proveedor;
    private SuscripcionPrimerCobroService service;
    private UUID usuarioId;
    private UUID suscripcionId;
    private ContextoCobro contexto;
    private static final Instant FECHA = Instant.parse("2026-02-01T02:30:00Z");

    @Test void primerCobroAnualSumaDoceMesesCalendarioIncluyendoAnioBisiesto() {
        var anual = new ContextoCobro(suscripcionId, "PREMIUM_ANUAL", "remoto", true, null);
        var aprobacion = Instant.parse("2028-02-29T15:00:00Z");
        when(transacciones.buscar(usuarioId, suscripcionId)).thenReturn(anual);
        when(proveedor.consultarPreapproval("remoto")).thenReturn(new PreapprovalConsultado(
                "remoto", suscripcionId.toString(), "authorized", null, new BigDecimal("40000"), "ARS", 12, "months"));
        when(proveedor.buscarCuotas("remoto")).thenReturn(List.of(new CuotaConsultada("remoto", "999")));
        var pago = pago("999", "approved", "40000", "ARS", aprobacion);
        when(proveedor.consultarPago("999")).thenReturn(pago);
        when(transacciones.confirmar(eq(usuarioId), eq(anual), eq(pago), any())).thenReturn(
                new ResultadoCobro(suscripcionId, EstadoCobro.ACTIVADA, UUID.randomUUID(), "999",
                        aprobacion, Instant.parse("2029-02-28T15:00:00Z")));
        service.confirmar(usuarioId, suscripcionId);
        verify(transacciones).confirmar(usuarioId, anual, pago, Instant.parse("2029-02-28T15:00:00Z"));
    }

    @BeforeEach
    void preparar() {
        usuarioId = UUID.randomUUID();
        suscripcionId = UUID.randomUUID();
        contexto = new ContextoCobro(suscripcionId, "PREMIUM_MENSUAL", "remoto", true, null);
        service = new SuscripcionPrimerCobroService(transacciones,
                new CatalogoPlanesSuscripcion("2500.00", "ARS", 1, "months", "Premium QA", "40000", "ARS", 12, "months", "Plan Premium anual MisMascotas"), proveedor);
        when(transacciones.buscar(usuarioId, suscripcionId)).thenReturn(contexto);
    }

    @Test
    void reservaAjenaOInexistenteNoConsultaAlProveedor() {
        when(transacciones.buscar(usuarioId, suscripcionId)).thenThrow(new RecursoNoEncontradoException("No encontrada"));
        assertThatThrownBy(() -> service.confirmar(usuarioId, suscripcionId))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(proveedor);
    }

    @Test
    void sinIdRemotoQuedaEnConciliacion() {
        when(transacciones.buscar(usuarioId, suscripcionId))
                .thenReturn(new ContextoCobro(suscripcionId, "PREMIUM_MENSUAL", null, true, null));
        assertThat(service.confirmar(usuarioId, suscripcionId).estado()).isEqualTo(EstadoCobro.PENDIENTE_CONCILIACION);
        verifyNoInteractions(proveedor);
    }

    @Test
    void confirmadaDevuelveElResultadoPersistidoSinConsultarNiExtender() {
        var existente = new ResultadoCobro(suscripcionId, EstadoCobro.YA_CONFIRMADA,
                UUID.randomUUID(), "999", FECHA, FECHA.plusSeconds(100));
        when(transacciones.buscar(usuarioId, suscripcionId))
                .thenReturn(new ContextoCobro(suscripcionId, "PREMIUM_MENSUAL", "remoto", false, existente));
        assertThat(service.confirmar(usuarioId, suscripcionId)).isSameAs(existente);
        verifyNoInteractions(proveedor);
        verify(transacciones, never()).confirmar(any(), any(), any(), any());
    }

    @Test
    void eligeElPrimeroPorFechaDeduplicaYUsaMesCalendarioEnBuenosAires() {
        remoto("authorized");
        when(proveedor.buscarCuotas("remoto")).thenReturn(List.of(
                new CuotaConsultada("remoto", "1000"), new CuotaConsultada("remoto", "999"),
                new CuotaConsultada("remoto", "999")));
        when(proveedor.consultarPago("999")).thenReturn(pago("999", "approved", "2500", "ARS", FECHA));
        when(proveedor.consultarPago("1000")).thenReturn(pago("1000", "approved", "2500", "ARS", FECHA.plusSeconds(10)));
        var esperado = new ResultadoCobro(suscripcionId, EstadoCobro.ACTIVADA, UUID.randomUUID(),
                "999", FECHA, Instant.parse("2026-03-01T02:30:00Z"));
        when(transacciones.confirmar(eq(usuarioId), eq(contexto), any(), eq(esperado.fechaVencimiento())))
                .thenReturn(esperado);

        assertThat(service.confirmar(usuarioId, suscripcionId)).isEqualTo(esperado);
        verify(proveedor, times(1)).consultarPago("999");
        verify(transacciones).confirmar(usuarioId, contexto, pago("999", "approved", "2500", "ARS", FECHA),
                Instant.parse("2026-03-01T02:30:00Z"));
    }

    @Test
    void authorizedSinCuotasNoActiva() {
        remoto("authorized");
        when(proveedor.buscarCuotas("remoto")).thenReturn(List.of());
        assertRechazo(EstadoCobro.SIN_COBRO_APROBADO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rejected", "cancelled"})
    void pagoNoAprobadoNoActiva(String estado) {
        candidato(pago("999", estado, "2500", "ARS", null));
        assertRechazo(EstadoCobro.SIN_COBRO_APROBADO);
    }

    @Test
    void pagoPendienteImpideElegirOtroCobroComoPrimero() {
        candidato(pago("999", "pending", "2500", "ARS", null));
        assertRechazo(EstadoCobro.NO_SE_PUDO_VERIFICAR);
    }

    @Test
    void cuotaAjenaNoActiva() {
        remoto("authorized");
        when(proveedor.buscarCuotas("remoto")).thenReturn(List.of(new CuotaConsultada("ajeno", "999")));
        assertRechazo(EstadoCobro.PENDIENTE_CONCILIACION);
        verify(proveedor, never()).consultarPago(any());
    }

    @Test
    void pagoConIdDistintoNoActiva() {
        candidato(pago("ajeno", "approved", "2500", "ARS", FECHA));
        assertRechazo(EstadoCobro.PENDIENTE_CONCILIACION);
    }

    @Test
    void importeDistintoNoActiva() {
        candidato(pago("999", "approved", "1", "ARS", FECHA));
        assertRechazo(EstadoCobro.PENDIENTE_CONCILIACION);
    }

    @Test
    void monedaDistintaNoActiva() {
        candidato(pago("999", "approved", "2500", "USD", FECHA));
        assertRechazo(EstadoCobro.PENDIENTE_CONCILIACION);
    }

    @Test
    void fechaAusenteNoActiva() {
        candidato(pago("999", "approved", "2500", "ARS", null));
        assertRechazo(EstadoCobro.PENDIENTE_CONCILIACION);
    }

    @Test
    void dosPagosConMismaFechaNoPermitenIdentificarElPrimero() {
        candidato(pago("999", "approved", "2500", "ARS", FECHA));
        when(proveedor.buscarCuotas("remoto")).thenReturn(List.of(
                new CuotaConsultada("remoto", "999"), new CuotaConsultada("remoto", "1000")));
        when(proveedor.consultarPago("1000")).thenReturn(pago("1000", "approved", "2500", "ARS", FECHA));
        assertRechazo(EstadoCobro.PENDIENTE_CONCILIACION);
    }

    @Test
    void respuestaInciertaPermiteReintentarSinActivar() {
        when(proveedor.consultarPreapproval("remoto")).thenThrow(new IllegalStateException("timeout simulado"));
        assertRechazo(EstadoCobro.NO_SE_PUDO_VERIFICAR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "cancelled", "unexpected"})
    void estadoRemotoNoAutorizadoNoConsultaPagos(String estado) {
        remoto(estado);
        assertRechazo("pending".equals(estado) ? EstadoCobro.SIN_COBRO_APROBADO : EstadoCobro.PENDIENTE_CONCILIACION);
        verify(proveedor, never()).buscarCuotas(any());
    }

    @Test
    void referenciaAjenaNoConsultaCuotas() {
        when(proveedor.consultarPreapproval("remoto")).thenReturn(new PreapprovalConsultado(
                "remoto", UUID.randomUUID().toString(), "authorized", null,
                new BigDecimal("2500"), "ARS", 1, "months"));
        assertRechazo(EstadoCobro.PENDIENTE_CONCILIACION);
    }

    @Test
    void condicionesRemotasDistintasNoActiva() {
        when(proveedor.consultarPreapproval("remoto")).thenReturn(new PreapprovalConsultado(
                "remoto", suscripcionId.toString(), "authorized", null,
                new BigDecimal("2500"), "ARS", 2, "months"));
        assertRechazo(EstadoCobro.PENDIENTE_CONCILIACION);
    }


    @Test
    void conservaElDiagnosticoDePagoPendienteDentroDeLaConfirmacion() {
        candidato(pago("999", "pending", "2500", "ARS", null));
        var detalle = service.confirmarConDetalle(usuarioId, suscripcionId);
        assertThat(detalle.tipo()).isEqualTo(SuscripcionPrimerCobroService.TipoVerificacion.COBRO_PENDIENTE);
        assertThat(detalle.resultado().estado()).isEqualTo(EstadoCobro.NO_SE_PUDO_VERIFICAR);
    }

    @Test
    void conservaLaRevisionParaUnEstadoDePagoFueraDelAlcance() {
        candidato(pago("999", "refunded", "2500", "ARS", null));
        var detalle = service.confirmarConDetalle(usuarioId, suscripcionId);
        assertThat(detalle.tipo()).isEqualTo(SuscripcionPrimerCobroService.TipoVerificacion.CONCILIACION);
        assertThat(detalle.resultado().estado()).isEqualTo(EstadoCobro.NO_SE_PUDO_VERIFICAR);
    }

    private void assertRechazo(EstadoCobro esperado) {
        assertThat(service.confirmar(usuarioId, suscripcionId).estado()).isEqualTo(esperado);
        verify(transacciones, never()).confirmar(any(), any(), any(), any());
    }

    private void remoto(String estado) {
        when(proveedor.consultarPreapproval("remoto")).thenReturn(new PreapprovalConsultado(
                "remoto", suscripcionId.toString(), estado, null,
                new BigDecimal("2500.0"), "ARS", 1, "months"));
    }

    private void candidato(PagoConsultado pago) {
        remoto("authorized");
        when(proveedor.buscarCuotas("remoto")).thenReturn(List.of(new CuotaConsultada("remoto", "999")));
        when(proveedor.consultarPago("999")).thenReturn(pago);
    }

    private PagoConsultado pago(String id, String estado, String monto, String moneda, Instant fecha) {
        return new PagoConsultado(id, estado, new BigDecimal(monto), moneda, fecha);
    }
}
