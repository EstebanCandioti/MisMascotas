package com.MisMascotas.backend.Controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.MercadoPagoException;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PreapprovalCreado;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PreapprovalConsultado;
import com.MisMascotas.backend.Repository.UsuarioRepository;
import com.MisMascotas.backend.Security.JwtFilter;
import com.MisMascotas.backend.Security.JwtService;
import com.MisMascotas.backend.Security.SecurityConfig;
import com.MisMascotas.backend.Service.CatalogoPlanesSuscripcion;
import com.MisMascotas.backend.Service.CustomUserDetailsService;
import com.MisMascotas.backend.Service.SuscripcionPrimerCobroService;
import com.MisMascotas.backend.Service.SuscripcionReservaService;
import com.MisMascotas.backend.Service.SuscripcionReservaService.ReservaSuscripcion;
import com.MisMascotas.backend.Service.SuscripcionReservaService.ReservaParaContinuar;
import com.MisMascotas.backend.Service.SuscripcionReservaService.EstadoReservaReemplazo;
import com.MisMascotas.backend.Service.SuscripcionReservaService.ResultadoReservaReemplazo;
import com.MisMascotas.backend.Service.SuscripcionService;
import com.MisMascotas.backend.Service.SuscripcionCobroTransaccionalService.EstadoCobro;
import com.MisMascotas.backend.Service.SuscripcionCobroTransaccionalService.ResultadoCobro;
import com.MisMascotas.backend.Service.UsuarioAutenticadoService;

@WebMvcTest(controllers = SuscripcionController.class, properties = {
        "suscripciones.planes.premium-mensual.importe=2500.00"
})
@Import({ SecurityConfig.class, JwtFilter.class, CustomUserDetailsService.class,
        UsuarioAutenticadoService.class, SuscripcionService.class, CatalogoPlanesSuscripcion.class })
class SuscripcionControllerTests {

    private static final String EMAIL = "suscripciones-qa@example.test";
    private static final String PLAN = "PREMIUM_MENSUAL";
    private static final String SOLICITUD = "{\"tipoPlan\":\"PREMIUM_MENSUAL\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UsuarioRepository usuarios;
    @MockitoBean private SuscripcionReservaService reservas;
    @MockitoBean private MercadoPagoGateway proveedor;
    @MockitoBean private SuscripcionPrimerCobroService primerCobro;
    @MockitoBean private JwtService jwtService;

    private UUID usuarioId;
    private UUID suscripcionId;

    @BeforeEach
    void preparar() {
        usuarioId = UUID.randomUUID();
        suscripcionId = UUID.randomUUID();
    }

    @Test
    void inicioAutenticadoEntregaElCheckoutDespuesDeGuardarElIdRemoto() throws Exception {
        autenticarUsuario();
        when(reservas.reservar(usuarioId, PLAN)).thenReturn(reserva(null, true));
        when(proveedor.crearPreapproval(any())).thenReturn(
                new PreapprovalCreado("preapproval-qa", "https://checkout.example.test/qa"));

        mockMvc.perform(post("/suscripciones/iniciar").with(user(EMAIL))
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSuscripcion").value(suscripcionId.toString()))
                .andExpect(jsonPath("$.estado").value("CHECKOUT_DISPONIBLE"))
                .andExpect(jsonPath("$.initPoint").value("https://checkout.example.test/qa"));

        var orden = inOrder(usuarios, reservas, proveedor);
        orden.verify(usuarios).findByEmail(EMAIL);
        orden.verify(reservas).reservar(usuarioId, PLAN);
        orden.verify(proveedor).crearPreapproval(any());
        orden.verify(reservas).confirmarIdMercadoPago(suscripcionId, "preapproval-qa");
        verifyNoInteractions(primerCobro);
        verify(usuarios, never()).save(any());
    }

    @Test
    void repetirElInicioConservaElIntentoYNoCreaOtroPreapproval() throws Exception {
        autenticarUsuario();
        when(reservas.reservar(usuarioId, PLAN))
                .thenReturn(reserva(null, true), reserva("preapproval-qa", false));
        when(proveedor.crearPreapproval(any())).thenReturn(
                new PreapprovalCreado("preapproval-qa", "https://checkout.example.test/qa"));

        mockMvc.perform(post("/suscripciones/iniciar").with(user(EMAIL))
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CHECKOUT_DISPONIBLE"));
        mockMvc.perform(post("/suscripciones/iniciar").with(user(EMAIL))
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSuscripcion").value(suscripcionId.toString()))
                .andExpect(jsonPath("$.estado").value("INTENTO_EN_CURSO"))
                .andExpect(jsonPath("$.initPoint").value(nullValue()));

        verify(proveedor, times(1)).crearPreapproval(any());
        verifyNoInteractions(primerCobro);
    }

    @Test
    void reservaSinIdRemotoConservaSuIdYQuedaEnConciliacion() throws Exception {
        autenticarUsuario();
        when(reservas.reservar(usuarioId, PLAN)).thenReturn(reserva(null, false));

        mockMvc.perform(post("/suscripciones/iniciar").with(user(EMAIL))
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSuscripcion").value(suscripcionId.toString()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_CONCILIACION"))
                .andExpect(jsonPath("$.initPoint").value(nullValue()));

        verify(proveedor, never()).crearPreapproval(any());
        verifyNoInteractions(primerCobro);
    }

    @Test
    void respuestaInciertaDelProveedorDevuelveResultadoFuncionalSinEnlace() throws Exception {
        autenticarUsuario();
        when(reservas.reservar(usuarioId, PLAN)).thenReturn(reserva(null, true));
        when(proveedor.crearPreapproval(any())).thenThrow(new MercadoPagoException("Timeout simulado"));

        mockMvc.perform(post("/suscripciones/iniciar").with(user(EMAIL))
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSuscripcion").value(suscripcionId.toString()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_CONCILIACION"))
                .andExpect(jsonPath("$.initPoint").value(nullValue()));

        verifyNoInteractions(primerCobro);
    }

    @ParameterizedTest
    @ValueSource(strings = { "{}", "{\"tipoPlan\":null}", "{\"tipoPlan\":\"\"}", "{\"tipoPlan\":\"   \"}" })
    void tipoPlanAusenteONoInformadoDevuelveErrorDeValidacion(String cuerpo) throws Exception {
        mockMvc.perform(post("/suscripciones/iniciar").with(user(EMAIL))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ERROR_VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value(containsString("tipoPlan:")));

        verifyNoInteractions(usuarios, reservas, proveedor, primerCobro);
    }

    @Test
    void unPlanFueraDelCatalogoDevuelve400SinReservarNiLlamarAlProveedor() throws Exception {
        autenticarUsuario();

        mockMvc.perform(post("/suscripciones/iniciar").with(user(EMAIL))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tipoPlan\":\"PLAN_INEXISTENTE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ERROR_VALIDACION"));

        verifyNoInteractions(reservas, proveedor, primerCobro);
    }

    @Test
    void sinSesionDevuelve401AntesDeEjecutarElInicio() throws Exception {
        mockMvc.perform(post("/suscripciones/iniciar")
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(usuarios, reservas, proveedor, primerCobro);
    }

    @Test
    void tokenDePreautenticacionNoHabilitaElInicio() throws Exception {
        when(jwtService.esTokenPreAuth("token-qa-preauth")).thenReturn(true);

        mockMvc.perform(post("/suscripciones/iniciar")
                        .header("Authorization", "Bearer token-qa-preauth")
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(usuarios, reservas, proveedor, primerCobro);
    }

    @Test
    void continuarDevuelveElCheckoutExistenteSinCrearNiModificarLaReserva() throws Exception {
        prepararContinuacion("preapproval-existente", true);
        when(proveedor.consultarPreapproval("preapproval-existente"))
                .thenReturn(preapproval("pending", "2500.0"));

        mockMvc.perform(post("/suscripciones/{idSuscripcion}/continuar", suscripcionId).with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSuscripcion").value(suscripcionId.toString()))
                .andExpect(jsonPath("$.estado").value("CHECKOUT_DISPONIBLE"))
                .andExpect(jsonPath("$.initPoint").value("https://checkout.example.test/existente"));

        verify(proveedor).consultarPreapproval("preapproval-existente");
        verifyNoMoreInteractions(proveedor);
        verificarContinuacionSinEscrituras();
    }

    @ParameterizedTest
    @CsvSource({
            "authorized, REQUIERE_VERIFICAR_PRIMER_COBRO",
            "cancelled, NO_CONTINUABLE",
            "estado-inesperado, NO_SE_PUDO_CONFIRMAR"
    })
    void continuarConservaLosEstadosRemotosSinEntregarEnlace(String remoto, String esperado) throws Exception {
        prepararContinuacion("preapproval-existente", true);
        when(proveedor.consultarPreapproval("preapproval-existente")).thenReturn(preapproval(remoto, "2500"));

        mockMvc.perform(post("/suscripciones/{idSuscripcion}/continuar", suscripcionId).with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSuscripcion").value(suscripcionId.toString()))
                .andExpect(jsonPath("$.estado").value(esperado))
                .andExpect(jsonPath("$.initPoint").value(nullValue()));

        verificarContinuacionSinEscrituras();
    }

    @Test
    void continuarSinIdRemotoDevuelveConciliacionSinConsultarAlProveedor() throws Exception {
        prepararContinuacion(null, true);

        mockMvc.perform(post("/suscripciones/{idSuscripcion}/continuar", suscripcionId).with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSuscripcion").value(suscripcionId.toString()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_CONCILIACION"))
                .andExpect(jsonPath("$.initPoint").value(nullValue()));

        verifyNoInteractions(proveedor);
        verificarContinuacionSinEscrituras();
    }

    @Test
    void continuarReservaNoPendienteDevuelveNoContinuableSinConsultarAlProveedor() throws Exception {
        prepararContinuacion("preapproval-existente", false);

        mockMvc.perform(post("/suscripciones/{idSuscripcion}/continuar", suscripcionId).with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NO_CONTINUABLE"))
                .andExpect(jsonPath("$.initPoint").value(nullValue()));

        verifyNoInteractions(proveedor);
        verificarContinuacionSinEscrituras();
    }

    @Test
    void continuarConCondicionesDistintasDevuelveNoContinuableSinEnlace() throws Exception {
        prepararContinuacion("preapproval-existente", true);
        when(proveedor.consultarPreapproval("preapproval-existente")).thenReturn(preapproval("pending", "3000"));

        mockMvc.perform(post("/suscripciones/{idSuscripcion}/continuar", suscripcionId).with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NO_CONTINUABLE"))
                .andExpect(jsonPath("$.initPoint").value(nullValue()));

        verificarContinuacionSinEscrituras();
    }

    @Test
    void continuarConErrorDeProveedorDevuelveNoSePudoConfirmar() throws Exception {
        prepararContinuacion("preapproval-existente", true);
        when(proveedor.consultarPreapproval("preapproval-existente"))
                .thenThrow(new MercadoPagoException("Timeout simulado"));

        mockMvc.perform(post("/suscripciones/{idSuscripcion}/continuar", suscripcionId).with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSuscripcion").value(suscripcionId.toString()))
                .andExpect(jsonPath("$.estado").value("NO_SE_PUDO_CONFIRMAR"))
                .andExpect(jsonPath("$.initPoint").value(nullValue()));

        verificarContinuacionSinEscrituras();
    }

    @ParameterizedTest
    @ValueSource(strings = { "Intento ajeno", "Intento inexistente" })
    void continuarIntentoAjenoOInexistenteDevuelve404AntesDeConsultarAlProveedor(String motivo) throws Exception {
        autenticarUsuario();
        when(reservas.buscarParaContinuar(usuarioId, suscripcionId))
                .thenThrow(new RecursoNoEncontradoException(motivo));

        mockMvc.perform(post("/suscripciones/{idSuscripcion}/continuar", suscripcionId).with(user(EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));

        verifyNoInteractions(proveedor);
        verificarContinuacionSinEscrituras();
    }

    @Test
    void continuarSinSesionDevuelve401AntesDeConsultarLaReserva() throws Exception {
        mockMvc.perform(post("/suscripciones/{idSuscripcion}/continuar", suscripcionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(usuarios, reservas, proveedor, primerCobro);
    }

    @Test
    void continuarConUuidInvalidoDevuelve400SinConsultarLaReserva() throws Exception {
        mockMvc.perform(post("/suscripciones/no-es-un-uuid/continuar").with(user(EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ERROR_VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value(containsString("idSuscripcion:")));

        verifyNoInteractions(usuarios, reservas, proveedor, primerCobro);
    }

    @Test
    void reemplazarCancelaElAnteriorYEntregaLosDosIdsYElCheckoutNuevo() throws Exception {
        UUID idNueva = UUID.randomUUID();
        prepararContinuacion("preapproval-existente", true);
        when(proveedor.consultarPreapproval("preapproval-existente"))
                .thenReturn(preapproval("pending", "2500"), preapproval("cancelled", "2500"));
        prepararReservaDeReemplazo(idNueva, EstadoReservaReemplazo.CREADA);
        String enlace = "https://checkout.example.test/?preapproval_id=preapproval-nuevo";
        when(proveedor.crearPreapproval(any())).thenReturn(new PreapprovalCreado("preapproval-nuevo", enlace));

        esperarReemplazo("CHECKOUT_DISPONIBLE", idNueva, enlace);

        var orden = inOrder(reservas, proveedor);
        orden.verify(reservas).buscarParaContinuar(usuarioId, suscripcionId);
        orden.verify(proveedor).consultarPreapproval("preapproval-existente");
        orden.verify(proveedor).cancelarPreapproval("preapproval-existente");
        orden.verify(proveedor).consultarPreapproval("preapproval-existente");
        orden.verify(reservas).reemplazarTrasCancelacionConfirmada(usuarioId, suscripcionId, "preapproval-existente");
        orden.verify(proveedor).crearPreapproval(any());
        orden.verify(reservas).confirmarIdMercadoPago(idNueva, "preapproval-nuevo");
        verify(proveedor, times(1)).crearPreapproval(any());
    }

    @Test
    void reemplazarSinIdRemotoConservaConciliacionConIdNuevoNulo() throws Exception {
        prepararContinuacion(null, true);

        esperarReemplazo("PENDIENTE_CONCILIACION", null, null);

        verifyNoInteractions(proveedor);
        verify(reservas).buscarParaContinuar(usuarioId, suscripcionId);
        verifyNoMoreInteractions(reservas);
    }

    @Test
    void reemplazarConPostInciertoConservaElIdNuevoEnConciliacion() throws Exception {
        UUID idNueva = UUID.randomUUID();
        prepararContinuacion("preapproval-existente", true);
        when(proveedor.consultarPreapproval("preapproval-existente"))
                .thenReturn(preapproval("cancelled", "2500"));
        prepararReservaDeReemplazo(idNueva, EstadoReservaReemplazo.CREADA);
        when(proveedor.crearPreapproval(any())).thenThrow(new MercadoPagoException("Respuesta incierta simulada"));

        esperarReemplazo("PENDIENTE_CONCILIACION", idNueva, null);

        verify(proveedor, times(1)).crearPreapproval(any());
        verify(reservas, never()).confirmarIdMercadoPago(any(), any());
    }

    @Test
    void reemplazarAutorizadoRequiereVerificarCobroSinEnlaceNiNuevoIntento() throws Exception {
        prepararContinuacion("preapproval-existente", true);
        when(proveedor.consultarPreapproval("preapproval-existente"))
                .thenReturn(preapproval("authorized", "2500"));

        esperarReemplazo("REQUIERE_VERIFICAR_PRIMER_COBRO", null, null);

        verify(proveedor, never()).cancelarPreapproval(any());
        verify(proveedor, never()).crearPreapproval(any());
        verify(reservas, never()).reemplazarTrasCancelacionConfirmada(any(), any(), any());
    }

    @Test
    void reemplazarConCancelacionNoConfirmadaDevuelve200SinIdNuevoNiEnlace() throws Exception {
        prepararContinuacion("preapproval-existente", true);
        when(proveedor.consultarPreapproval("preapproval-existente"))
                .thenReturn(preapproval("pending", "2500"));

        esperarReemplazo("CANCELACION_NO_CONFIRMADA", null, null);

        verify(proveedor).cancelarPreapproval("preapproval-existente");
        verify(proveedor, never()).crearPreapproval(any());
        verify(reservas, never()).reemplazarTrasCancelacionConfirmada(any(), any(), any());
    }

    @Test
    void reemplazarConOtroIntentoEnCursoConservaAmbosIdsSinEnlace() throws Exception {
        UUID idNueva = UUID.randomUUID();
        prepararContinuacion("preapproval-existente", true);
        when(proveedor.consultarPreapproval("preapproval-existente"))
                .thenReturn(preapproval("cancelled", "2500"));
        prepararReservaDeReemplazo(idNueva, EstadoReservaReemplazo.INTENTO_PENDIENTE);

        esperarReemplazo("INTENTO_EN_CURSO", idNueva, null);

        verify(proveedor, never()).crearPreapproval(any());
    }

    @Test
    void reemplazarReservaNoPendienteDevuelveNoReemplazableSinConsultarAlProveedor() throws Exception {
        prepararContinuacion("preapproval-existente", false);

        esperarReemplazo("NO_REEMPLAZABLE", null, null);

        verifyNoInteractions(proveedor);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Intento ajeno", "Intento inexistente" })
    void reemplazarIntentoAjenoOInexistenteDevuelve404AntesDeConsultarAlProveedor(String motivo) throws Exception {
        autenticarUsuario();
        when(reservas.buscarParaContinuar(usuarioId, suscripcionId))
                .thenThrow(new RecursoNoEncontradoException(motivo));

        mockMvc.perform(post("/suscripciones/{idSuscripcion}/reemplazar", suscripcionId).with(user(EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));

        verifyNoInteractions(proveedor, primerCobro);
        verify(reservas).buscarParaContinuar(usuarioId, suscripcionId);
        verifyNoMoreInteractions(reservas);
    }

    @Test
    void reemplazarSinSesionDevuelve401SinConsultarLaReserva() throws Exception {
        mockMvc.perform(post("/suscripciones/{idSuscripcion}/reemplazar", suscripcionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(usuarios, reservas, proveedor, primerCobro);
    }

    @Test
    void reemplazarConUuidInvalidoDevuelve400SinConsultarLaReserva() throws Exception {
        mockMvc.perform(post("/suscripciones/no-es-un-uuid/reemplazar").with(user(EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ERROR_VALIDACION"));

        verifyNoInteractions(usuarios, reservas, proveedor, primerCobro);
    }

    private void esperarReemplazo(String estado, UUID idNueva, String enlace) throws Exception {
        mockMvc.perform(post("/suscripciones/{idSuscripcion}/reemplazar", suscripcionId).with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSuscripcionAnterior").value(suscripcionId.toString()))
                .andExpect(jsonPath("$.idSuscripcionNueva").value(
                        idNueva == null ? nullValue() : org.hamcrest.Matchers.is(idNueva.toString())))
                .andExpect(jsonPath("$.estado").value(estado))
                .andExpect(jsonPath("$.initPoint").value(
                        enlace == null ? nullValue() : org.hamcrest.Matchers.is(enlace)));
        verifyNoInteractions(primerCobro);
    }

    @ParameterizedTest
    @EnumSource(EstadoCobro.class)
    void confirmarPrimerCobroConservaLosCincoResultadosYSusValoresNulos(EstadoCobro estado) throws Exception {
        autenticarUsuario();
        boolean tienePago = estado == EstadoCobro.ACTIVADA || estado == EstadoCobro.YA_CONFIRMADA;
        ResultadoCobro resultado = tienePago
                ? new ResultadoCobro(suscripcionId, estado, UUID.randomUUID(), "123456789",
                        Instant.parse("2026-02-01T02:30:00Z"), Instant.parse("2026-03-01T02:30:00Z"))
                : ResultadoCobro.sinPago(suscripcionId, estado);
        when(primerCobro.confirmar(usuarioId, suscripcionId)).thenReturn(resultado);

        esperarConfirmacion(resultado);

        verify(primerCobro).confirmar(usuarioId, suscripcionId);
        verifyNoMoreInteractions(primerCobro);
        verifyNoInteractions(reservas, proveedor);
    }

    @Test
    void segundaConfirmacionDevuelveElMismoPagoYVencimientoConYaConfirmada() throws Exception {
        autenticarUsuario();
        UUID idPago = UUID.randomUUID();
        Instant fechaPago = Instant.parse("2026-02-01T02:30:00Z");
        Instant vencimiento = Instant.parse("2026-03-01T02:30:00Z");
        ResultadoCobro activada = new ResultadoCobro(suscripcionId, EstadoCobro.ACTIVADA,
                idPago, "123456789", fechaPago, vencimiento);
        ResultadoCobro repetida = new ResultadoCobro(suscripcionId, EstadoCobro.YA_CONFIRMADA,
                idPago, "123456789", fechaPago, vencimiento);
        when(primerCobro.confirmar(usuarioId, suscripcionId)).thenReturn(activada, repetida);

        esperarConfirmacion(activada);
        esperarConfirmacion(repetida);

        verify(primerCobro, times(2)).confirmar(usuarioId, suscripcionId);
        verifyNoInteractions(reservas, proveedor);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Intento ajeno", "Intento inexistente" })
    void confirmarIntentoAjenoOInexistenteDevuelve404SinConsultarAlProveedor(String motivo) throws Exception {
        autenticarUsuario();
        when(primerCobro.confirmar(usuarioId, suscripcionId))
                .thenThrow(new RecursoNoEncontradoException(motivo));

        mockMvc.perform(post("/suscripciones/{idSuscripcion}/confirmar-primer-cobro", suscripcionId)
                        .with(user(EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));

        verify(primerCobro).confirmar(usuarioId, suscripcionId);
        verifyNoInteractions(reservas, proveedor);
    }

    @Test
    void confirmarSinSesionDevuelve401AntesDeConsultarLaSuscripcion() throws Exception {
        mockMvc.perform(post("/suscripciones/{idSuscripcion}/confirmar-primer-cobro", suscripcionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(usuarios, reservas, proveedor, primerCobro);
    }

    @Test
    void confirmarConTokenDePreautenticacionDevuelve401() throws Exception {
        when(jwtService.esTokenPreAuth("token-qa-preauth")).thenReturn(true);

        mockMvc.perform(post("/suscripciones/{idSuscripcion}/confirmar-primer-cobro", suscripcionId)
                        .header("Authorization", "Bearer token-qa-preauth"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(usuarios, reservas, proveedor, primerCobro);
    }

    @Test
    void confirmarConTokenNoReconocidoDevuelve401() throws Exception {
        mockMvc.perform(post("/suscripciones/{idSuscripcion}/confirmar-primer-cobro", suscripcionId)
                        .header("Authorization", "Bearer token-qa-invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(usuarios, reservas, proveedor, primerCobro);
    }

    @Test
    void confirmarConUuidInvalidoDevuelve400SinConsultarLaSuscripcion() throws Exception {
        mockMvc.perform(post("/suscripciones/no-es-un-uuid/confirmar-primer-cobro").with(user(EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ERROR_VALIDACION"));

        verifyNoInteractions(usuarios, reservas, proveedor, primerCobro);
    }

    private void esperarConfirmacion(ResultadoCobro esperado) throws Exception {
        mockMvc.perform(post("/suscripciones/{idSuscripcion}/confirmar-primer-cobro", suscripcionId)
                        .with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSuscripcion").value(esperado.idSuscripcion().toString()))
                .andExpect(jsonPath("$.estado").value(esperado.estado().name()))
                .andExpect(jsonPath("$.idPago").value(esperado.idPago() == null
                        ? nullValue() : org.hamcrest.Matchers.is(esperado.idPago().toString())))
                .andExpect(jsonPath("$.idPagoMercadoPago").value(esperado.idPagoMercadoPago() == null
                        ? nullValue() : org.hamcrest.Matchers.is(esperado.idPagoMercadoPago())))
                .andExpect(jsonPath("$.fechaPago").value(esperado.fechaPago() == null
                        ? nullValue() : org.hamcrest.Matchers.is(esperado.fechaPago().toString())))
                .andExpect(jsonPath("$.fechaVencimiento").value(esperado.fechaVencimiento() == null
                        ? nullValue() : org.hamcrest.Matchers.is(esperado.fechaVencimiento().toString())));
    }

    private void prepararReservaDeReemplazo(UUID idNueva, EstadoReservaReemplazo estado) {
        when(reservas.reemplazarTrasCancelacionConfirmada(usuarioId, suscripcionId, "preapproval-existente"))
                .thenReturn(new ResultadoReservaReemplazo(estado,
                        new ReservaSuscripcion(idNueva, EMAIL, PLAN, null, estado == EstadoReservaReemplazo.CREADA)));
    }

    private void prepararContinuacion(String idRemoto, boolean pendiente) {
        autenticarUsuario();
        when(reservas.buscarParaContinuar(usuarioId, suscripcionId))
                .thenReturn(new ReservaParaContinuar(suscripcionId, PLAN, idRemoto, pendiente));
    }

    private PreapprovalConsultado preapproval(String estado, String importe) {
        return new PreapprovalConsultado("preapproval-existente", suscripcionId.toString(), estado,
                "https://checkout.example.test/existente", new BigDecimal(importe), "ARS", 1, "months");
    }

    private void verificarContinuacionSinEscrituras() {
        verify(reservas).buscarParaContinuar(usuarioId, suscripcionId);
        verifyNoMoreInteractions(reservas);
        verify(proveedor, never()).crearPreapproval(any());
        verify(proveedor, never()).cancelarPreapproval(any());
        verify(usuarios, never()).save(any());
        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(primerCobro);
    }

    private void autenticarUsuario() {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(usuarioId);
        usuario.setEmail(EMAIL);
        when(usuarios.findByEmail(EMAIL)).thenReturn(usuario);
    }

    private ReservaSuscripcion reserva(String idRemoto, boolean nueva) {
        return new ReservaSuscripcion(suscripcionId, EMAIL, PLAN, idRemoto, nueva);
    }
}
