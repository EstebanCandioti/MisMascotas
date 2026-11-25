package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.*;
import org.springframework.web.client.*;
import com.MisMascotas.backend.Entity.Suscripcion;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.MercadoPagoException;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.*;
import com.MisMascotas.backend.Repository.*;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.*;

class ClasificacionNotificacionMercadoPagoTests {
    final MercadoPagoGateway proveedor = mock(MercadoPagoGateway.class);
    final SuscripcionRepository suscripciones = mock(SuscripcionRepository.class);
    final SuscripcionCicloTransaccionalService transacciones = mock(SuscripcionCicloTransaccionalService.class);
    ProcesadorNotificacionMercadoPagoService procesador;
    final UUID id = UUID.randomUUID();
    @BeforeEach void preparar() {
        var ciclo = new SuscripcionCicloService(proveedor, new CatalogoPlanesSuscripcion("2500", "ARS", 1, "months", "QA", "40000", "ARS", 12, "months", "Plan Premium anual MisMascotas"), suscripciones, transacciones);
        procesador = new ProcesadorNotificacionMercadoPagoService(mock(NotificacionMercadoPagoRepository.class), suscripciones,
                proveedor, ciclo, mock(NotificacionMercadoPagoMarcadoService.class));
        var s = new Suscripcion(); s.setIdSuscripcion(id); s.setUsuario(new Usuario()); s.setTipoPlan("PREMIUM_MENSUAL"); s.setIdSuscripcionMercadopago("s");
        when(suscripciones.findByIdSuscripcionMercadopagoConUsuario("s")).thenReturn(Optional.of(s));
        when(proveedor.consultarFacturaAutorizada("f")).thenReturn(new FacturaAutorizada("f", "s", id.toString(), "p", "processed", 1, null, null, new BigDecimal("2500"), "ARS"));
        when(proveedor.consultarPago("p")).thenReturn(new PagoConsultado("p", "approved", new BigDecimal("2500"), "ARS", Instant.EPOCH));
    }
    @ParameterizedTest @MethodSource("errores")
    void conservaHttpYRetryAfterEnTodasLasConsultas(String etapa, int codigo) {
        var h = new HttpHeaders(); h.set("Retry-After", "120"); var status = HttpStatus.valueOf(codigo);
        var ex = codigo >= 500 ? HttpServerErrorException.create(status, "QA", h, new byte[0], StandardCharsets.UTF_8)
                : HttpClientErrorException.create(status, "QA", h, new byte[0], StandardCharsets.UTF_8);
        provocar(etapa, new MercadoPagoException("QA", ex));
        var resultado = evaluar(etapa);
        assertThat(resultado.motivo()).isEqualTo("HTTP_" + codigo);
        assertThat(resultado.estado()).isEqualTo(codigo == 429 || codigo >= 500 ? Estado.PENDIENTE : Estado.REVISION);
        if (codigo == 429) assertThat(resultado.retryAfter()).isEqualTo("120");
        verifyNoInteractions(transacciones);
    }
    @ParameterizedTest @ValueSource(strings={"FACTURA", "PAGO", "SUSCRIPCION", "CAMBIO"})
    void transporteEsTemporal(String etapa) {
        provocar(etapa, new MercadoPagoException("QA", new ResourceAccessException("Timeout")));
        assertThat(evaluar(etapa).reintento()).isEqualTo(TipoReintento.TEMPORAL);
    }
    @ParameterizedTest @ValueSource(strings={"FACTURA", "PAGO", "SUSCRIPCION", "CAMBIO"})
    void respuestaInvalidaRequiereRevision(String etapa) {
        provocar(etapa, new MercadoPagoException("QA"));
        assertThat(evaluar(etapa).estado()).isEqualTo(Estado.REVISION);
    }
    ResultadoProcesamiento evaluar(String etapa) { return procesador.evaluar(new NotificacionMercadoPagoRepository.NotificacionMercadoPago("n",
            "CAMBIO".equals(etapa) ? "subscription_preapproval" : "subscription_authorized_payment", "CAMBIO".equals(etapa) ? "s" : "f", null)); }
    void provocar(String etapa, MercadoPagoException ex) {
        switch(etapa) {
            case "FACTURA" -> when(proveedor.consultarFacturaAutorizada("f")).thenThrow(ex);
            case "PAGO" -> when(proveedor.consultarPago("p")).thenThrow(ex);
            default -> when(proveedor.consultarPreapproval("s")).thenThrow(ex);
        }
    }
    static Stream<Arguments> errores() { return Stream.of("FACTURA", "PAGO", "SUSCRIPCION", "CAMBIO").flatMap(e ->
            Stream.of(400,401,403,404,422,429,500,503).map(c -> Arguments.of(e,c))); }
}
