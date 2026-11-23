package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.MisMascotas.backend.Exception.MercadoPagoException;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PreapprovalConsultado;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PreapprovalCreado;
import com.MisMascotas.backend.Service.SuscripcionReservaService.ReservaParaContinuar;
import com.MisMascotas.backend.Service.SuscripcionReservaService.ReservaSuscripcion;
import com.MisMascotas.backend.Service.SuscripcionReservaService.EstadoReservaReemplazo;
import com.MisMascotas.backend.Service.SuscripcionReservaService.ResultadoReservaReemplazo;
import com.MisMascotas.backend.Service.SuscripcionService.EstadoAltaSuscripcion;
import com.MisMascotas.backend.Service.SuscripcionService.EstadoContinuacionSuscripcion;
import com.MisMascotas.backend.Service.SuscripcionService.EstadoReemplazoSuscripcion;

@ExtendWith(MockitoExtension.class)
class SuscripcionServiceTests {

    @Mock
    private CatalogoPlanesSuscripcion catalogoPlanes;
    @Mock
    private SuscripcionReservaService reservaService;
    @Mock
    private MercadoPagoGateway mercadoPagoGateway;

    private SuscripcionService service;
    private UUID usuarioId;
    private UUID suscripcionId;

    @BeforeEach
    void configurar() {
        service = new SuscripcionService(catalogoPlanes, reservaService, mercadoPagoGateway,
                org.mockito.Mockito.mock(SuscripcionPrimerCobroService.class));
        usuarioId = UUID.randomUUID();
        suscripcionId = UUID.randomUUID();
        lenient().when(catalogoPlanes.resolver(CatalogoPlanesSuscripcion.PREMIUM_MENSUAL))
                .thenReturn(plan());
    }

    @Test
    void guardaIdRemotoAntesDeEntregarCheckout() {
        when(reservaService.reservar(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL))
                .thenReturn(reservaNueva());
        when(mercadoPagoGateway.crearPreapproval(any()))
                .thenReturn(new PreapprovalCreado("preapproval-1", "https://checkout.test/1"));

        var resultado = service.iniciarAlta(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL);

        assertThat(resultado.estado()).isEqualTo(EstadoAltaSuscripcion.CHECKOUT_DISPONIBLE);
        assertThat(resultado.initPoint()).isEqualTo("https://checkout.test/1");
        InOrder orden = inOrder(reservaService, mercadoPagoGateway);
        orden.verify(reservaService).reservar(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL);
        orden.verify(mercadoPagoGateway).crearPreapproval(any());
        orden.verify(reservaService).confirmarIdMercadoPago(suscripcionId, "preapproval-1");
    }

    @Test
    void reservaExistenteSinIdQuedaEnConciliacionSinNuevoPost() {
        when(reservaService.reservar(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL))
                .thenReturn(new ReservaSuscripcion(
                        suscripcionId,
                        "qa@example.test",
                        CatalogoPlanesSuscripcion.PREMIUM_MENSUAL,
                        null,
                        false));

        var resultado = service.iniciarAlta(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL);

        assertThat(resultado.estado()).isEqualTo(EstadoAltaSuscripcion.PENDIENTE_CONCILIACION);
        assertThat(resultado.initPoint()).isNull();
        verify(mercadoPagoGateway, never()).crearPreapproval(any());
    }

    @Test
    void reservaExistenteConIdInformaIntentoEnCursoSinNuevoPost() {
        when(reservaService.reservar(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL))
                .thenReturn(new ReservaSuscripcion(
                        suscripcionId,
                        "qa@example.test",
                        CatalogoPlanesSuscripcion.PREMIUM_MENSUAL,
                        "preapproval-1",
                        false));

        var resultado = service.iniciarAlta(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL);

        assertThat(resultado.estado()).isEqualTo(EstadoAltaSuscripcion.INTENTO_EN_CURSO);
        verify(mercadoPagoGateway, never()).crearPreapproval(any());
    }

    @Test
    void respuestaInciertaConservaReservaYReintentoNoEmiteSegundoPost() {
        when(reservaService.reservar(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL))
                .thenReturn(reservaNueva())
                .thenReturn(new ReservaSuscripcion(
                        suscripcionId,
                        "qa@example.test",
                        CatalogoPlanesSuscripcion.PREMIUM_MENSUAL,
                        null,
                        false));
        when(mercadoPagoGateway.crearPreapproval(any()))
                .thenThrow(new MercadoPagoException("respuesta incierta"));

        var primero = service.iniciarAlta(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL);
        var reintento = service.iniciarAlta(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL);

        assertThat(primero.estado()).isEqualTo(EstadoAltaSuscripcion.PENDIENTE_CONCILIACION);
        assertThat(reintento.estado()).isEqualTo(EstadoAltaSuscripcion.PENDIENTE_CONCILIACION);
        verify(mercadoPagoGateway, times(1)).crearPreapproval(any());
        verify(reservaService, never()).confirmarIdMercadoPago(any(), any());
    }

    @Test
    void falloAlGuardarIdRemotoNoEntregaCheckout() {
        when(reservaService.reservar(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL))
                .thenReturn(reservaNueva());
        when(mercadoPagoGateway.crearPreapproval(any()))
                .thenReturn(new PreapprovalCreado("preapproval-1", "https://checkout.test/1"));
        org.mockito.Mockito.doThrow(new IllegalStateException("fallo de base"))
                .when(reservaService)
                .confirmarIdMercadoPago(suscripcionId, "preapproval-1");

        var resultado = service.iniciarAlta(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL);

        assertThat(resultado.estado()).isEqualTo(EstadoAltaSuscripcion.PENDIENTE_CONCILIACION);
        assertThat(resultado.initPoint()).isNull();
    }

    @Test
    void dosAltasSimultaneasReservadasEmitenUnSoloPost() throws Exception {
        CountDownLatch proveedorIniciado = new CountDownLatch(1);
        CountDownLatch liberarProveedor = new CountDownLatch(1);
        when(reservaService.reservar(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL))
                .thenReturn(reservaNueva())
                .thenReturn(new ReservaSuscripcion(
                        suscripcionId,
                        "qa@example.test",
                        CatalogoPlanesSuscripcion.PREMIUM_MENSUAL,
                        null,
                        false));
        when(mercadoPagoGateway.crearPreapproval(any())).thenAnswer(invocacion -> {
            proveedorIniciado.countDown();
            if (!liberarProveedor.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("La prueba no libero al proveedor");
            }
            return new PreapprovalCreado("preapproval-1", "https://checkout.test/1");
        });

        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var primera = ejecutor.submit(
                    () -> service.iniciarAlta(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL));
            assertThat(proveedorIniciado.await(5, TimeUnit.SECONDS)).isTrue();
            var segunda = ejecutor.submit(
                    () -> service.iniciarAlta(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL));

            assertThat(segunda.get(5, TimeUnit.SECONDS).estado())
                    .isEqualTo(EstadoAltaSuscripcion.PENDIENTE_CONCILIACION);
            liberarProveedor.countDown();
            assertThat(primera.get(5, TimeUnit.SECONDS).estado())
                    .isEqualTo(EstadoAltaSuscripcion.CHECKOUT_DISPONIBLE);
        }

        verify(mercadoPagoGateway, times(1)).crearPreapproval(any());
        verify(reservaService, times(1)).confirmarIdMercadoPago(suscripcionId, "preapproval-1");
    }

    @Test
    void continuarAltaCompatibleDevuelveCheckoutExistente() {
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenReturn(reservaParaContinuar("preapproval-1", true));
        when(mercadoPagoGateway.consultarPreapproval("preapproval-1"))
                .thenReturn(consulta("pending", new BigDecimal("2500.0"), "ARS", 1, "months"));

        var resultado = service.continuarAlta(usuarioId, suscripcionId);

        assertThat(resultado.estado()).isEqualTo(EstadoContinuacionSuscripcion.CHECKOUT_DISPONIBLE);
        assertThat(resultado.initPoint()).isEqualTo("https://checkout.test/existente");
        verify(reservaService, never()).reservar(any(), any());
        verify(reservaService, never()).confirmarIdMercadoPago(any(), any());
        verify(mercadoPagoGateway, never()).crearPreapproval(any());
    }

    @Test
    void continuarAltaSinIdRemotoQuedaPendienteDeConciliacionSinConsultar() {
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenReturn(reservaParaContinuar(null, true));

        var resultado = service.continuarAlta(usuarioId, suscripcionId);

        assertThat(resultado.estado()).isEqualTo(EstadoContinuacionSuscripcion.PENDIENTE_CONCILIACION);
        assertThat(resultado.initPoint()).isNull();
        verify(mercadoPagoGateway, never()).consultarPreapproval(any());
    }

    @Test
    void continuarAltaNoPendienteNoConsultaProveedor() {
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenReturn(reservaParaContinuar("preapproval-1", false));

        var resultado = service.continuarAlta(usuarioId, suscripcionId);

        assertThat(resultado.estado()).isEqualTo(EstadoContinuacionSuscripcion.NO_CONTINUABLE);
        verify(mercadoPagoGateway, never()).consultarPreapproval(any());
    }

    @Test
    void continuarAltaAjenaONoExistenteNoConsultaProveedor() {
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenThrow(new RecursoNoEncontradoException("Suscripcion no encontrada"));

        assertThatThrownBy(() -> service.continuarAlta(usuarioId, suscripcionId))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(mercadoPagoGateway, never()).consultarPreapproval(any());
    }

    @Test
    void continuarAltaAutorizadaRequiereVerificarPrimerCobro() {
        prepararConsulta("authorized");

        var resultado = service.continuarAlta(usuarioId, suscripcionId);

        assertThat(resultado.estado())
                .isEqualTo(EstadoContinuacionSuscripcion.REQUIERE_VERIFICAR_PRIMER_COBRO);
        assertThat(resultado.initPoint()).isNull();
    }

    @Test
    void continuarAltaCanceladaNoEsContinuable() {
        prepararConsulta("cancelled");

        var resultado = service.continuarAlta(usuarioId, suscripcionId);

        assertThat(resultado.estado()).isEqualTo(EstadoContinuacionSuscripcion.NO_CONTINUABLE);
        assertThat(resultado.initPoint()).isNull();
    }

    @Test
    void continuarAltaRechazaCadaCondicionDistintaDelPlan() {
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenReturn(reservaParaContinuar("preapproval-1", true));
        when(mercadoPagoGateway.consultarPreapproval("preapproval-1"))
                .thenReturn(
                        consulta("pending", new BigDecimal("2600.00"), "ARS", 1, "months"),
                        consulta("pending", new BigDecimal("2500.00"), "USD", 1, "months"),
                        consulta("pending", new BigDecimal("2500.00"), "ARS", 2, "months"),
                        consulta("pending", new BigDecimal("2500.00"), "ARS", 1, "days"));

        for (int variante = 0; variante < 4; variante++) {
            var resultado = service.continuarAlta(usuarioId, suscripcionId);
            assertThat(resultado.estado()).isEqualTo(EstadoContinuacionSuscripcion.NO_CONTINUABLE);
            assertThat(resultado.initPoint()).isNull();
        }
    }

    @Test
    void continuarAltaConReferenciaOIdInconsistenteNoSeConfirma() {
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenReturn(reservaParaContinuar("preapproval-1", true));
        PreapprovalConsultado idDistinto = new PreapprovalConsultado(
                "preapproval-ajeno",
                suscripcionId.toString(),
                "pending",
                "https://checkout.test/existente",
                new BigDecimal("2500.00"),
                "ARS",
                1,
                "months");
        PreapprovalConsultado referenciaDistinta = new PreapprovalConsultado(
                "preapproval-1",
                UUID.randomUUID().toString(),
                "pending",
                "https://checkout.test/existente",
                new BigDecimal("2500.00"),
                "ARS",
                1,
                "months");
        when(mercadoPagoGateway.consultarPreapproval("preapproval-1"))
                .thenReturn(idDistinto, referenciaDistinta);

        assertThat(service.continuarAlta(usuarioId, suscripcionId).estado())
                .isEqualTo(EstadoContinuacionSuscripcion.NO_SE_PUDO_CONFIRMAR);
        assertThat(service.continuarAlta(usuarioId, suscripcionId).estado())
                .isEqualTo(EstadoContinuacionSuscripcion.NO_SE_PUDO_CONFIRMAR);
    }

    @Test
    void continuarAltaConEstadoInesperadoNoSeConfirma() {
        prepararConsulta("paused");

        var resultado = service.continuarAlta(usuarioId, suscripcionId);

        assertThat(resultado.estado()).isEqualTo(EstadoContinuacionSuscripcion.NO_SE_PUDO_CONFIRMAR);
        assertThat(resultado.initPoint()).isNull();
    }

    @Test
    void continuarAltaConErrorDelProveedorConservaReserva() {
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenReturn(reservaParaContinuar("preapproval-1", true));
        when(mercadoPagoGateway.consultarPreapproval("preapproval-1"))
                .thenThrow(new MercadoPagoException("timeout ficticio"));

        var resultado = service.continuarAlta(usuarioId, suscripcionId);

        assertThat(resultado.estado()).isEqualTo(EstadoContinuacionSuscripcion.NO_SE_PUDO_CONFIRMAR);
        assertThat(resultado.initPoint()).isNull();
        verify(reservaService, never()).confirmarIdMercadoPago(any(), any());
    }

    @Test
    void reemplazoSinIdRemotoQuedaPendienteDeConciliacion() {
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenReturn(reservaParaContinuar(null, true));

        var resultado = service.reemplazarAlta(usuarioId, suscripcionId);

        assertThat(resultado.estado()).isEqualTo(EstadoReemplazoSuscripcion.PENDIENTE_CONCILIACION);
        verify(mercadoPagoGateway, never()).consultarPreapproval(any());
        verify(mercadoPagoGateway, never()).cancelarPreapproval(any());
        verify(mercadoPagoGateway, never()).crearPreapproval(any());
    }

    @Test
    void reemplazoAjenoONoExistenteNoConsultaProveedor() {
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenThrow(new RecursoNoEncontradoException("Suscripcion no encontrada"));

        assertThatThrownBy(() -> service.reemplazarAlta(usuarioId, suscripcionId))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(mercadoPagoGateway, never()).consultarPreapproval(any());
        verify(mercadoPagoGateway, never()).cancelarPreapproval(any());
        verify(mercadoPagoGateway, never()).crearPreapproval(any());
    }

    @Test
    void reemplazoConReferenciaInconsistenteNoCancelaNiCrea() {
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenReturn(reservaParaContinuar("preapproval-1", true));
        when(mercadoPagoGateway.consultarPreapproval("preapproval-1"))
                .thenReturn(new PreapprovalConsultado(
                        "preapproval-1",
                        UUID.randomUUID().toString(),
                        "pending",
                        "https://checkout.test/anterior",
                        new BigDecimal("2500.00"),
                        "ARS",
                        1,
                        "months"));

        var resultado = service.reemplazarAlta(usuarioId, suscripcionId);

        assertThat(resultado.estado()).isEqualTo(EstadoReemplazoSuscripcion.CANCELACION_NO_CONFIRMADA);
        verify(mercadoPagoGateway, never()).cancelarPreapproval(any());
        verify(mercadoPagoGateway, never()).crearPreapproval(any());
        verify(reservaService, never()).reemplazarTrasCancelacionConfirmada(any(), any(), any());
    }

    @Test
    void reemplazoExitosoCancelaConfirmaReservaYCreaNuevoPreapproval() {
        UUID idNueva = UUID.randomUUID();
        prepararReemplazoLocal(idNueva);
        when(mercadoPagoGateway.consultarPreapproval("preapproval-1"))
                .thenReturn(consulta("pending", new BigDecimal("2500.00"), "ARS", 1, "months"))
                .thenReturn(consulta("cancelled", new BigDecimal("2500.00"), "ARS", 1, "months"));
        when(mercadoPagoGateway.crearPreapproval(any()))
                .thenReturn(new PreapprovalCreado("preapproval-nuevo", "https://checkout.test/nuevo"));

        var resultado = service.reemplazarAlta(usuarioId, suscripcionId);

        assertThat(resultado.estado()).isEqualTo(EstadoReemplazoSuscripcion.CHECKOUT_DISPONIBLE);
        assertThat(resultado.idSuscripcionAnterior()).isEqualTo(suscripcionId);
        assertThat(resultado.idSuscripcionNueva()).isEqualTo(idNueva);
        assertThat(resultado.initPoint()).isEqualTo("https://checkout.test/nuevo");
        InOrder orden = inOrder(mercadoPagoGateway, reservaService);
        orden.verify(mercadoPagoGateway).consultarPreapproval("preapproval-1");
        orden.verify(mercadoPagoGateway).cancelarPreapproval("preapproval-1");
        orden.verify(mercadoPagoGateway).consultarPreapproval("preapproval-1");
        orden.verify(reservaService)
                .reemplazarTrasCancelacionConfirmada(usuarioId, suscripcionId, "preapproval-1");
        orden.verify(mercadoPagoGateway).crearPreapproval(any());
        orden.verify(reservaService).confirmarIdMercadoPago(idNueva, "preapproval-nuevo");
    }

    @Test
    void reemplazoYaCanceladoNoRepitePut() {
        UUID idNueva = UUID.randomUUID();
        prepararReemplazoLocal(idNueva);
        when(mercadoPagoGateway.consultarPreapproval("preapproval-1"))
                .thenReturn(consulta("cancelled", new BigDecimal("2500.00"), "ARS", 1, "months"));
        when(mercadoPagoGateway.crearPreapproval(any()))
                .thenReturn(new PreapprovalCreado("preapproval-nuevo", "https://checkout.test/nuevo"));

        var resultado = service.reemplazarAlta(usuarioId, suscripcionId);

        assertThat(resultado.estado()).isEqualTo(EstadoReemplazoSuscripcion.CHECKOUT_DISPONIBLE);
        verify(mercadoPagoGateway, never()).cancelarPreapproval(any());
        verify(mercadoPagoGateway, times(1)).consultarPreapproval("preapproval-1");
    }

    @Test
    void reemplazoAutorizadoRequiereVerificarCobroSinPost() {
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenReturn(reservaParaContinuar("preapproval-1", true));
        when(mercadoPagoGateway.consultarPreapproval("preapproval-1"))
                .thenReturn(consulta("authorized", new BigDecimal("2500.00"), "ARS", 1, "months"));

        var resultado = service.reemplazarAlta(usuarioId, suscripcionId);

        assertThat(resultado.estado())
                .isEqualTo(EstadoReemplazoSuscripcion.REQUIERE_VERIFICAR_PRIMER_COBRO);
        verify(mercadoPagoGateway, never()).cancelarPreapproval(any());
        verify(mercadoPagoGateway, never()).crearPreapproval(any());
        verify(reservaService, never()).reemplazarTrasCancelacionConfirmada(any(), any(), any());
    }

    @Test
    void respuestaPerdidaDelPutPuedeContinuarSiGetConfirmaCancelacion() {
        UUID idNueva = UUID.randomUUID();
        prepararReemplazoLocal(idNueva);
        when(mercadoPagoGateway.consultarPreapproval("preapproval-1"))
                .thenReturn(consulta("pending", new BigDecimal("2500.00"), "ARS", 1, "months"))
                .thenReturn(consulta("cancelled", new BigDecimal("2500.00"), "ARS", 1, "months"));
        org.mockito.Mockito.doThrow(new MercadoPagoException("respuesta perdida"))
                .when(mercadoPagoGateway)
                .cancelarPreapproval("preapproval-1");
        when(mercadoPagoGateway.crearPreapproval(any()))
                .thenReturn(new PreapprovalCreado("preapproval-nuevo", "https://checkout.test/nuevo"));

        var resultado = service.reemplazarAlta(usuarioId, suscripcionId);

        assertThat(resultado.estado()).isEqualTo(EstadoReemplazoSuscripcion.CHECKOUT_DISPONIBLE);
        verify(mercadoPagoGateway, times(2)).consultarPreapproval("preapproval-1");
        verify(mercadoPagoGateway, times(1)).crearPreapproval(any());
    }

    @Test
    void cancelacionNoConfirmadaConservaReservaYNoHacePost() {
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenReturn(reservaParaContinuar("preapproval-1", true));
        when(mercadoPagoGateway.consultarPreapproval("preapproval-1"))
                .thenReturn(consulta("pending", new BigDecimal("2500.00"), "ARS", 1, "months"))
                .thenReturn(consulta("pending", new BigDecimal("2500.00"), "ARS", 1, "months"));
        org.mockito.Mockito.doThrow(new MercadoPagoException("timeout ficticio"))
                .when(mercadoPagoGateway)
                .cancelarPreapproval("preapproval-1");

        var resultado = service.reemplazarAlta(usuarioId, suscripcionId);

        assertThat(resultado.estado()).isEqualTo(EstadoReemplazoSuscripcion.CANCELACION_NO_CONFIRMADA);
        verify(reservaService, never()).reemplazarTrasCancelacionConfirmada(any(), any(), any());
        verify(mercadoPagoGateway, never()).crearPreapproval(any());
    }

    @Test
    void autorizacionDuranteCancelacionDetieneElReemplazo() {
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenReturn(reservaParaContinuar("preapproval-1", true));
        when(mercadoPagoGateway.consultarPreapproval("preapproval-1"))
                .thenReturn(consulta("pending", new BigDecimal("2500.00"), "ARS", 1, "months"))
                .thenReturn(consulta("authorized", new BigDecimal("2500.00"), "ARS", 1, "months"));

        var resultado = service.reemplazarAlta(usuarioId, suscripcionId);

        assertThat(resultado.estado())
                .isEqualTo(EstadoReemplazoSuscripcion.REQUIERE_VERIFICAR_PRIMER_COBRO);
        verify(mercadoPagoGateway, never()).crearPreapproval(any());
    }

    @Test
    void respuestaPerdidaDelPostDeReemplazoNoGeneraOtroPostAlRepetir() {
        UUID idNueva = UUID.randomUUID();
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenReturn(reservaParaContinuar("preapproval-1", true))
                .thenReturn(reservaParaContinuar("preapproval-1", false));
        when(mercadoPagoGateway.consultarPreapproval("preapproval-1"))
                .thenReturn(consulta("cancelled", new BigDecimal("2500.00"), "ARS", 1, "months"));
        when(reservaService.reemplazarTrasCancelacionConfirmada(usuarioId, suscripcionId, "preapproval-1"))
                .thenReturn(reemplazoCreado(idNueva));
        when(mercadoPagoGateway.crearPreapproval(any()))
                .thenThrow(new MercadoPagoException("respuesta perdida"));

        var primero = service.reemplazarAlta(usuarioId, suscripcionId);
        var reintento = service.reemplazarAlta(usuarioId, suscripcionId);

        assertThat(primero.estado()).isEqualTo(EstadoReemplazoSuscripcion.PENDIENTE_CONCILIACION);
        assertThat(primero.idSuscripcionNueva()).isEqualTo(idNueva);
        assertThat(reintento.estado()).isEqualTo(EstadoReemplazoSuscripcion.NO_REEMPLAZABLE);
        verify(mercadoPagoGateway, times(1)).crearPreapproval(any());
    }

    @Test
    void dosReemplazosConcurrentesProducenComoMaximoUnPostNuevo() throws Exception {
        UUID idNueva = UUID.randomUUID();
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenReturn(reservaParaContinuar("preapproval-1", true));
        when(mercadoPagoGateway.consultarPreapproval("preapproval-1"))
                .thenReturn(consulta("cancelled", new BigDecimal("2500.00"), "ARS", 1, "months"));
        when(reservaService.reemplazarTrasCancelacionConfirmada(usuarioId, suscripcionId, "preapproval-1"))
                .thenReturn(
                        reemplazoCreado(idNueva),
                        new ResultadoReservaReemplazo(EstadoReservaReemplazo.YA_PROCESADO, null));
        when(mercadoPagoGateway.crearPreapproval(any()))
                .thenReturn(new PreapprovalCreado("preapproval-nuevo", "https://checkout.test/nuevo"));
        CountDownLatch inicio = new CountDownLatch(1);

        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var primera = ejecutor.submit(() -> {
                inicio.await();
                return service.reemplazarAlta(usuarioId, suscripcionId);
            });
            var segunda = ejecutor.submit(() -> {
                inicio.await();
                return service.reemplazarAlta(usuarioId, suscripcionId);
            });
            inicio.countDown();
            assertThat(primera.get(5, TimeUnit.SECONDS).estado())
                    .isIn(EstadoReemplazoSuscripcion.CHECKOUT_DISPONIBLE,
                            EstadoReemplazoSuscripcion.NO_REEMPLAZABLE);
            assertThat(segunda.get(5, TimeUnit.SECONDS).estado())
                    .isIn(EstadoReemplazoSuscripcion.CHECKOUT_DISPONIBLE,
                            EstadoReemplazoSuscripcion.NO_REEMPLAZABLE);
        }

        verify(mercadoPagoGateway, times(1)).crearPreapproval(any());
        verify(reservaService, times(1)).confirmarIdMercadoPago(idNueva, "preapproval-nuevo");
    }

    @Test
    void reemplazoConcurrenteConAltaInicialProduceComoMaximoUnPostNuevo() throws Exception {
        UUID idNueva = UUID.randomUUID();
        prepararReemplazoLocal(idNueva);
        when(mercadoPagoGateway.consultarPreapproval("preapproval-1"))
                .thenReturn(consulta("cancelled", new BigDecimal("2500.00"), "ARS", 1, "months"));
        when(reservaService.reservar(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL))
                .thenReturn(new ReservaSuscripcion(
                        idNueva,
                        "qa@example.test",
                        CatalogoPlanesSuscripcion.PREMIUM_MENSUAL,
                        null,
                        false));
        when(mercadoPagoGateway.crearPreapproval(any()))
                .thenReturn(new PreapprovalCreado("preapproval-nuevo", "https://checkout.test/nuevo"));
        CountDownLatch inicio = new CountDownLatch(1);

        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var reemplazo = ejecutor.submit(() -> {
                inicio.await();
                return service.reemplazarAlta(usuarioId, suscripcionId);
            });
            var alta = ejecutor.submit(() -> {
                inicio.await();
                return service.iniciarAlta(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL);
            });
            inicio.countDown();

            assertThat(reemplazo.get(5, TimeUnit.SECONDS).estado())
                    .isEqualTo(EstadoReemplazoSuscripcion.CHECKOUT_DISPONIBLE);
            assertThat(alta.get(5, TimeUnit.SECONDS).estado())
                    .isEqualTo(EstadoAltaSuscripcion.PENDIENTE_CONCILIACION);
        }

        verify(mercadoPagoGateway, times(1)).crearPreapproval(any());
        verify(reservaService, times(1)).confirmarIdMercadoPago(idNueva, "preapproval-nuevo");
    }

    private ReservaSuscripcion reservaNueva() {
        return new ReservaSuscripcion(
                suscripcionId,
                "qa@example.test",
                CatalogoPlanesSuscripcion.PREMIUM_MENSUAL,
                null,
                true);
    }

    private ReservaParaContinuar reservaParaContinuar(String idMercadoPago, boolean pendiente) {
        return new ReservaParaContinuar(
                suscripcionId,
                CatalogoPlanesSuscripcion.PREMIUM_MENSUAL,
                idMercadoPago,
                pendiente);
    }

    private void prepararReemplazoLocal(UUID idNueva) {
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenReturn(reservaParaContinuar("preapproval-1", true));
        when(reservaService.reemplazarTrasCancelacionConfirmada(usuarioId, suscripcionId, "preapproval-1"))
                .thenReturn(reemplazoCreado(idNueva));
    }

    private ResultadoReservaReemplazo reemplazoCreado(UUID idNueva) {
        return new ResultadoReservaReemplazo(
                EstadoReservaReemplazo.CREADA,
                new ReservaSuscripcion(
                        idNueva,
                        "qa@example.test",
                        CatalogoPlanesSuscripcion.PREMIUM_MENSUAL,
                        null,
                        true));
    }

    private void prepararConsulta(String estado) {
        when(reservaService.buscarParaContinuar(usuarioId, suscripcionId))
                .thenReturn(reservaParaContinuar("preapproval-1", true));
        when(mercadoPagoGateway.consultarPreapproval("preapproval-1"))
                .thenReturn(consulta(estado, new BigDecimal("2500.00"), "ARS", 1, "months"));
    }

    private PreapprovalConsultado consulta(
            String estado,
            BigDecimal importe,
            String moneda,
            int frecuencia,
            String tipoFrecuencia) {
        return new PreapprovalConsultado(
                "preapproval-1",
                suscripcionId.toString(),
                estado,
                "https://checkout.test/existente",
                importe,
                moneda,
                frecuencia,
                tipoFrecuencia);
    }

    private PlanSuscripcion plan() {
        return new PlanSuscripcion(
                CatalogoPlanesSuscripcion.PREMIUM_MENSUAL,
                "Plan Premium MisMascotas",
                new BigDecimal("2500.00"),
                "ARS",
                1,
                "months");
    }
}
