package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.MisMascotas.backend.Entity.Suscripcion;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.*;
import com.MisMascotas.backend.Repository.*;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.*;

@ExtendWith(MockitoExtension.class)
class ProcesadorNotificacionMercadoPagoServiceTests {
    @Mock NotificacionMercadoPagoRepository notificaciones;
    @Mock SuscripcionRepository suscripciones;
    @Mock MercadoPagoGateway proveedor;
    @Mock SuscripcionCicloService ciclo;
    @Mock NotificacionMercadoPagoMarcadoService marcado;
    ProcesadorNotificacionMercadoPagoService service;
    Suscripcion local;
    final UUID id = UUID.randomUUID();
    final NotificacionMercadoPagoRepository.NotificacionMercadoPago n =
            new NotificacionMercadoPagoRepository.NotificacionMercadoPago("n", "subscription_authorized_payment", "f", null);
    @BeforeEach void preparar() {
        service = new ProcesadorNotificacionMercadoPagoService(notificaciones, suscripciones, proveedor, ciclo, marcado);
        local = new Suscripcion(); local.setIdSuscripcion(id); local.setUsuario(new Usuario());
        lenient().when(suscripciones.findByIdSuscripcionMercadopagoConUsuario("s")).thenReturn(Optional.of(local));
        lenient().when(notificaciones.buscar("n")).thenReturn(Optional.of(n));
    }
    @ParameterizedTest @MethodSource("combinaciones")
    void clasificaLaFacturaJuntoConElPago(String factura, String pago, Estado estado, String motivo) {
        prepararFactura(factura, pago);
        var resultado = service.evaluar(n);
        assertThat(resultado.estado()).isEqualTo(estado);
        assertThat(resultado.motivo()).isEqualTo(motivo);
        assertThat(resultado.observacion().estadoFactura()).isEqualTo(factura);
        assertThat(resultado.observacion().estadoPago()).isEqualTo(pago);
        verifyNoInteractions(ciclo, marcado);
    }
    static Stream<Arguments> combinaciones() {
        return Stream.of(
            Arguments.of("recycling", "rejected", Estado.PENDIENTE, "ESPERANDO_REINTENTO_MP"),
            Arguments.of("waiting for gateway", "in_process", Estado.PENDIENTE, "ESPERANDO_RESOLUCION_MP"),
            Arguments.of("waiting for gateway", "pending", Estado.PENDIENTE, "ESPERANDO_RESOLUCION_MP"),
            Arguments.of("waiting for gateway", null, Estado.PENDIENTE, "ESPERANDO_RESOLUCION_MP"),
            Arguments.of("processed", "rejected", Estado.COMPLETADA, "CUOTA_SIN_COBRO"),
            Arguments.of("scheduled", null, Estado.PENDIENTE, "COBRO_SIN_COBRO_APROBADO"),
            Arguments.of("processed", "refunded", Estado.REVISION, "PAGO_REQUIERE_REVISION"),
            Arguments.of("processed", "charged_back", Estado.REVISION, "PAGO_REQUIERE_REVISION"),
            Arguments.of("processed", "cancelled", Estado.REVISION, "PAGO_REQUIERE_REVISION"),
            Arguments.of("recycling", "approved", Estado.REVISION, "ESTADO_INCONSISTENTE"),
            Arguments.of("desconocido", "rejected", Estado.REVISION, "ESTADO_INCONSISTENTE"),
            Arguments.of(null, "rejected", Estado.REVISION, "ESTADO_FACTURA_AUSENTE"));
    }
    @Test void procesaElCobroDeLaFacturaYMarcaDespuesDeConfirmarlo() {
        prepararFactura("processed", "approved");
        when(ciclo.registrar(eq(local), any(), any())).thenReturn(ResultadoProcesamiento.completada("RENOVADA"));
        assertThat(service.procesar("n").motivo()).isEqualTo("RENOVADA");
        var orden = inOrder(ciclo, marcado); orden.verify(ciclo).registrar(eq(local), any(), any());
        orden.verify(marcado).marcarProcesada("n");
    }
    @Test void falloAlMarcarSeRecuperaConLaIdempotenciaDelCobro() {
        prepararFactura("processed", "approved");
        when(ciclo.registrar(eq(local), any(), any())).thenReturn(ResultadoProcesamiento.completada("ACTIVADA"), ResultadoProcesamiento.completada("YA_CONFIRMADA"));
        doThrow(new IllegalStateException("QA")).doReturn(true).when(marcado).marcarProcesada("n");
        assertThat(service.procesar("n").motivo()).isEqualTo("NO_SE_PUDO_MARCAR_PROCESADA");
        assertThat(service.procesar("n").motivo()).isEqualTo("YA_CONFIRMADA");
    }
    @Test void repeticionCompletadaNoConsultaMercadoPago() {
        when(notificaciones.buscar("n")).thenReturn(Optional.of(new NotificacionMercadoPagoRepository.NotificacionMercadoPago("n", n.topico(), "f", Instant.EPOCH)));
        assertThat(service.procesar("n").estado()).isEqualTo(Estado.PROCESADA_PREVIAMENTE);
        verifyNoInteractions(proveedor, ciclo, marcado);
    }
    @Test void facturaDeOtraIdentidadPasaARevision() {
        when(proveedor.consultarFacturaAutorizada("f")).thenReturn(new FacturaAutorizada("otra", "s", id.toString()));
        assertThat(service.evaluar(n).motivo()).isEqualTo("FACTURA_INCONSISTENTE");
        verifyNoInteractions(ciclo, marcado);
    }
    @Test void pagoConOtroIdNoSeAcredita() {
        prepararFactura("processed", "approved");
        when(proveedor.consultarPago("p")).thenReturn(new PagoConsultado("otro", "approved", BigDecimal.ONE, "ARS", Instant.EPOCH));
        assertThat(service.evaluar(n).motivo()).isEqualTo("PAGO_INCONSISTENTE");
        verifyNoInteractions(ciclo, marcado);
    }
    @Test void cambiosDeSuscripcionUsanElMismoProcesador() {
        when(ciclo.sincronizar("s")).thenReturn(ResultadoProcesamiento.completada("SUSCRIPCION_PAUSADA"));
        var resultado = service.evaluar(new NotificacionMercadoPagoRepository.NotificacionMercadoPago("n2", "subscription_preapproval", "s", null));
        assertThat(resultado.motivo()).isEqualTo("SUSCRIPCION_PAUSADA"); verifyNoInteractions(proveedor);
    }
    void prepararFactura(String estado, String pago) {
        when(proveedor.consultarFacturaAutorizada("f")).thenReturn(new FacturaAutorizada("f", "s", id.toString(),
                pago == null ? null : "p", estado, 1, Instant.EPOCH, null, new BigDecimal("2500"), "ARS"));
        if (pago != null) when(proveedor.consultarPago("p")).thenReturn(new PagoConsultado("p", pago, new BigDecimal("2500"), "ARS", Instant.EPOCH, "detalle"));
    }
}
