package com.MisMascotas.backend.Controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.MisMascotas.backend.Repository.NotificacionMercadoPagoRepository;
import com.MisMascotas.backend.Repository.NotificacionMercadoPagoRepository.ResultadoRegistro;
import com.MisMascotas.backend.Repository.UsuarioRepository;
import com.MisMascotas.backend.Security.JwtFilter;
import com.MisMascotas.backend.Security.JwtService;
import com.MisMascotas.backend.Security.SecurityConfig;
import com.MisMascotas.backend.Service.CustomUserDetailsService;
import com.MisMascotas.backend.Service.MercadoPagoWebhookService;
import com.MisMascotas.backend.Service.SuscripcionService;
import com.MisMascotas.backend.Service.UsuarioAutenticadoService;

import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = { MercadoPagoWebhookController.class, SuscripcionController.class }, properties = {
        "mercadopago.webhook.secret=secreto-ficticio-mismascotas"
})
@Import({ SecurityConfig.class, JwtFilter.class, CustomUserDetailsService.class, MercadoPagoWebhookService.class })
class MercadoPagoWebhookControllerTests {

    private static final String RUTA = "/webhooks/mercado-pago";
    private static final String FIRMA = "ts=1704908010,v1=de029c13ba6f5a3eb2f103e5a5562903fa77c957ca2f9181bb48ca90974cf1aa";
    private static final String CUERPO = """
            {"id":"notificacion-qa-001","type":"subscription_authorized_payment","data":{"id":"7034059187"}}
            """;

    @Autowired private MockMvc mvc;
    @MockitoBean private NotificacionMercadoPagoRepository notificaciones;
    @MockitoBean private PlatformTransactionManager transacciones;
    @MockitoBean private UsuarioRepository usuarios;
    @MockitoBean private JwtService jwt;
    @MockitoBean private SuscripcionService suscripciones;
    @MockitoBean private UsuarioAutenticadoService autenticado;
    @MockitoBean private com.MisMascotas.backend.Service.SuscripcionConsultaService consulta;
    @MockitoBean private com.MisMascotas.backend.Service.SuscripcionCancelacionService cancelacion;

    @BeforeEach
    void preparar() {
        when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(notificaciones.registrar(anyString(), anyString(), anyString())).thenReturn(ResultadoRegistro.REGISTRADA);
    }

    @Test
    void firmaValidaGuardaSinJwtYConfirmaAntesDeResponder() throws Exception {
        mvc.perform(firmada(CUERPO).header("Authorization", "Bearer ajeno-al-webhook"))
                .andExpect(status().isOk());

        var orden = inOrder(transacciones, notificaciones);
        orden.verify(transacciones).getTransaction(argThat(definicion ->
                definicion.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        orden.verify(notificaciones).registrar("notificacion-qa-001", "subscription_authorized_payment", "7034059187");
        orden.verify(transacciones).commit(any());
        verifyNoInteractions(jwt, suscripciones);
    }

    @Test
    void reenvioConfirmaLaTransaccionSinCambiarLaFilaOriginal() throws Exception {
        when(notificaciones.registrar(anyString(), anyString(), anyString())).thenReturn(ResultadoRegistro.YA_RECIBIDA);
        mvc.perform(firmada(CUERPO)).andExpect(status().isOk());
        verify(transacciones).commit(any());
        verify(notificaciones).registrar("notificacion-qa-001", "subscription_authorized_payment", "7034059187");
        verifyNoMoreInteractions(notificaciones);
    }

    @Test
    void aceptaIdsEnterosSinConfundirNotificacionConFactura() throws Exception {
        mvc.perform(firmada("""
                {"id":9876543210,"type":"subscription_authorized_payment","data":{"id":7034059187}}
                """)).andExpect(status().isOk());
        verify(notificaciones).registrar("9876543210", "subscription_authorized_payment", "7034059187");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "incorrecta", "ts=1704908010,v1=00" })
    void rechazaFirmaInvalida(String firma) throws Exception {
        mvc.perform(post(RUTA).param("data.id", "7034059187").header("x-request-id", "qa-webhook-001")
                        .header("x-signature", firma).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(notificaciones, transacciones);
    }

    @Test
    void firmaAusenteNoGuardaAunqueElJsonEsteMal() throws Exception {
        mvc.perform(post(RUTA).param("data.id", "7034059187").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(notificaciones, transacciones);
    }

    @ParameterizedTest
    @ValueSource(strings = { "data.id", "x-request-id", "x-signature" })
    void rechazaCadaDatoDeFirmaAusente(String ausente) throws Exception {
        var solicitud = post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO);
        if (!ausente.equals("data.id")) solicitud.param("data.id", "7034059187");
        if (!ausente.equals("x-request-id")) solicitud.header("x-request-id", "qa-webhook-001");
        if (!ausente.equals("x-signature")) solicitud.header("x-signature", FIRMA);
        mvc.perform(solicitud).andExpect(status().isUnauthorized());
        verifyNoInteractions(notificaciones, transacciones);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{", "null", "[]", "", "{\"type\":42}",
            "{\"type\":\"subscription_authorized_payment\",\"data\":{\"id\":7034059187}}",
            "{\"id\":\"n\",\"type\":\"subscription_authorized_payment\"}",
            "{\"id\":\"n\",\"type\":\"subscription_authorized_payment\",\"data\":{\"id\":123}}",
            "{\"id\":true,\"type\":\"subscription_authorized_payment\",\"data\":{\"id\":7034059187}}",
            "{\"id\":1.2,\"type\":\"subscription_authorized_payment\",\"data\":{\"id\":7034059187}}",
            "{\"id\":\" \",\"type\":\"subscription_authorized_payment\",\"data\":{\"id\":7034059187}}",
            "{\"id\":\"n\",\"type\":\"subscription_authorized_payment\",\"data\":{\"id\":7034059187.0}}",
            "{\"id\":\"n\",\"type\":\"subscription_authorized_payment\",\"data\":{\"id\":null}}"
    })
    void jsonFirmadoIncompletoOInconsistenteNoGuarda(String cuerpo) throws Exception {
        mvc.perform(firmada(cuerpo)).andExpect(status().isBadRequest());
        verifyNoInteractions(notificaciones, transacciones);
    }

    @Test
    void cambioDeSuscripcionFirmadoSeEncolaAntesDelAcuse() throws Exception {
        mvc.perform(firmada(CUERPO.replace("subscription_authorized_payment", "subscription_preapproval")))
                .andExpect(status().isOk());
        var orden = inOrder(notificaciones, transacciones);
        orden.verify(notificaciones).registrar("notificacion-qa-001", "subscription_preapproval", "7034059187");
        orden.verify(transacciones).commit(any());
    }

    @Test
    void otroTopicoFirmadoSeAcusaSinEncolar() throws Exception {
        mvc.perform(firmada(CUERPO.replace("subscription_authorized_payment", "subscription_preapproval_plan")))
                .andExpect(status().isOk());
        verifyNoInteractions(notificaciones, transacciones);
    }

    @Test
    void errorAlInsertarDevuelve503YRevierte() throws Exception {
        when(notificaciones.registrar(anyString(), anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("Fallo simulado"));
        mvc.perform(firmada(CUERPO)).andExpect(status().isServiceUnavailable());
        verify(transacciones).rollback(any());
        verify(transacciones, never()).commit(any());
    }

    @Test
    void errorAlConfirmarNuncaAcusaRecepcion() throws Exception {
        doThrow(new TransactionSystemException("Commit simulado fallido")).when(transacciones).commit(any());
        mvc.perform(firmada(CUERPO)).andExpect(status().isServiceUnavailable());
        verify(notificaciones).registrar(anyString(), anyString(), anyString());
    }

    @Test
    void errorAlAbrirTransaccionDevuelve503() throws Exception {
        when(transacciones.getTransaction(any())).thenThrow(new TransactionSystemException("Sin conexion simulada"));
        mvc.perform(firmada(CUERPO)).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(notificaciones);
    }

    @Test
    void secretoAusenteDevuelve503SinAccederALaBase() throws Exception {
        // Contexto local sin modificar la configuracion compartida de seguridad de las demas pruebas.
        var servicio = new MercadoPagoWebhookService(JsonMapper.builder().build(), notificaciones, transacciones, "");
        var sinSecreto = MockMvcBuilders.standaloneSetup(new MercadoPagoWebhookController(servicio)).build();
        sinSecreto.perform(firmada(CUERPO)).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(notificaciones, transacciones);
    }

    @ParameterizedTest
    @ValueSource(strings = { "GET", "PUT", "PATCH", "DELETE" })
    void otrosMetodosDelWebhookSiguenProtegidos(String metodo) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(metodo), RUTA)).andExpect(status().isUnauthorized());
        verifyNoInteractions(notificaciones, suscripciones);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/webhooks/otro", "/webhooks/mercado-pago/otro", "/pagos/webhook",
            "/suscripciones/iniciar",
            "/suscripciones/00000000-0000-0000-0000-000000000001/continuar",
            "/suscripciones/00000000-0000-0000-0000-000000000001/reemplazar",
            "/suscripciones/00000000-0000-0000-0000-000000000001/confirmar-primer-cobro" })
    void ningunaOtraRutaPostQuedaPublica(String ruta) throws Exception {
        mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content("{\"tipoPlan\":\"PREMIUM_MENSUAL\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(notificaciones, suscripciones);
    }

    private MockHttpServletRequestBuilder firmada(String cuerpo) {
        return post(RUTA).servletPath(RUTA).param("data.id", "7034059187")
                .header("x-request-id", "qa-webhook-001").header("x-signature", FIRMA)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }
}
