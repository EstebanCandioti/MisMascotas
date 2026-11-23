package com.MisMascotas.backend.Integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.MisMascotas.backend.Exception.MercadoPagoException;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.SolicitudPreapproval;

@ExtendWith(OutputCaptureExtension.class)
class MercadoPagoClientTests {

    @Test
    void consultaFacturaConIdDeFacturaYConservaPreapprovalYReferencia() {
        servidor.expect(requestTo("https://api.mercadopago.test/authorized_payments/7034059187"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer TEST-token-ficticio"))
                .andRespond(withSuccess("""
                        {"id":7034059187,"status":"processed","preapproval_id":"preapproval-qa",
                         "external_reference":"100d51e0-1879-49e5-9b25-3e0313a112d2"}
                        """, MediaType.APPLICATION_JSON));
        var factura = cliente.consultarFacturaAutorizada("7034059187");
        assertThat(factura.id()).isEqualTo("7034059187");
        assertThat(factura.idPreapproval()).isEqualTo("preapproval-qa");
        assertThat(factura.referenciaExterna()).isEqualTo("100d51e0-1879-49e5-9b25-3e0313a112d2");
        servidor.verify();
    }

    @Test
    void rechazaFacturaIncompleta() {
        servidor.expect(requestTo("https://api.mercadopago.test/authorized_payments/7034059187"))
                .andRespond(withSuccess("{\"id\":7034059187}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> cliente.consultarFacturaAutorizada("7034059187"))
                .isInstanceOf(MercadoPagoException.class);
        servidor.verify();
    }

    @Test
    void buscaTodasLasPaginasYConservaElPaymentIdDistintoDelIdDeCuota() {
        servidor.expect(requestTo("https://api.mercadopago.test/authorized_payments/search?preapproval_id=remoto&offset=0"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer TEST-token-ficticio"))
                .andRespond(withSuccess("""
                        {"paging":{"offset":0,"limit":1,"total":2},"results":[
                        {"id":111,"preapproval_id":"remoto","payment":{"id":999}}]}
                        """, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://api.mercadopago.test/authorized_payments/search?preapproval_id=remoto&offset=1"))
                .andRespond(withSuccess("""
                        {"paging":{"offset":1,"limit":1,"total":2},"results":[
                        {"id":222,"preapproval_id":"remoto","payment":{"id":888}}]}
                        """, MediaType.APPLICATION_JSON));

        var cuotas = cliente.buscarCuotas("remoto");

        assertThat(cuotas).extracting(MercadoPagoGateway.CuotaConsultada::idPago).containsExactly("999", "888");
        servidor.verify();
    }

    @Test
    void consultaPagoConIdNumericoEInterpretaElOffsetDeAprobacion() {
        servidor.expect(requestTo("https://api.mercadopago.test/v1/payments/999"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer TEST-token-ficticio"))
                .andRespond(withSuccess("""
                        {"id":999,"status":"approved","transaction_amount":2500.00,
                         "currency_id":"ARS","date_approved":"2026-01-31T23:30:00-03:00"}
                        """, MediaType.APPLICATION_JSON));

        var pago = cliente.consultarPago("999");

        assertThat(pago.id()).isEqualTo("999");
        assertThat(pago.importe()).isEqualByComparingTo("2500");
        assertThat(pago.fechaAprobacion()).isEqualTo(java.time.Instant.parse("2026-02-01T02:30:00Z"));
        servidor.verify();
    }

    @Test
    void rechazaCuotaDeOtraSuscripcionAunqueLaBusquedaEsteFiltrada() {
        servidor.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(withSuccess("""
                        {"paging":{"offset":0,"total":1},"results":[
                        {"preapproval_id":"ajeno","payment":{"id":999}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.buscarCuotas("remoto")).isInstanceOf(MercadoPagoException.class);
        servidor.verify();
    }

    @Test
    void paginaIncompletaNoSeConfundeConAusenciaDePagos() {
        servidor.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(withSuccess("""
                        {"paging":{"offset":0,"total":1},"results":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.buscarCuotas("remoto")).isInstanceOf(MercadoPagoException.class);
        servidor.verify();
    }

    @Test
    void errorHttpAlConsultarPagoSeInformaSinExponerLaRespuesta() {
        servidor.expect(requestTo("https://api.mercadopago.test/v1/payments/999"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("respuesta privada del proveedor"));

        assertThatThrownBy(() -> cliente.consultarPago("999"))
                .isInstanceOf(MercadoPagoException.class)
                .hasMessage("No se pudo verificar el detalle del pago");
        servidor.verify();
    }

    @Test
    void fechaDeAprobacionIninterpretableNoSeAcepta() {
        servidor.expect(requestTo("https://api.mercadopago.test/v1/payments/999"))
                .andRespond(withSuccess("""
                        {"id":999,"status":"approved","transaction_amount":2500,
                         "currency_id":"ARS","date_approved":"invalida"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.consultarPago("999")).isInstanceOf(MercadoPagoException.class);
        servidor.verify();
    }

    @Test
    void importeTextualDelContratoOficialSeComparaComoDecimal() {
        servidor.expect(requestTo("https://api.mercadopago.test/v1/payments/999"))
                .andRespond(withSuccess("""
                        {"id":999,"status":"approved","transaction_amount":"2500.00",
                         "currency_id":"ARS","date_approved":"2026-01-10T12:00:00Z"}
                        """, MediaType.APPLICATION_JSON));
        assertThat(cliente.consultarPago("999").importe()).isEqualByComparingTo("2500");
        servidor.verify();
    }

    @Test
    void unPagoIndicadoPorLaCuotaSinIdNoSeIgnora() {
        servidor.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(withSuccess("""
                        {"paging":{"offset":0,"total":1},"results":[
                        {"preapproval_id":"remoto","payment":{"status":"approved"}}]}
                        """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> cliente.buscarCuotas("remoto")).isInstanceOf(MercadoPagoException.class);
        servidor.verify();
    }

    @Test
    void preapprovalAutorizadoPuedeVerificarseSinEnlaceDeCheckout() {
        servidor.expect(requestTo("https://api.mercadopago.test/preapproval/remoto"))
                .andRespond(withSuccess("""
                        {"id":"remoto","external_reference":"uuid-local","status":"authorized",
                         "auto_recurring":{"frequency":1,"frequency_type":"months",
                         "transaction_amount":2500,"currency_id":"ARS"}}
                        """, MediaType.APPLICATION_JSON));
        assertThat(cliente.consultarPreapproval("remoto").estado()).isEqualTo("authorized");
        servidor.verify();
    }

    private MockRestServiceServer servidor;
    private MercadoPagoClient cliente;

    @BeforeEach
    void configurar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new MercadoPagoClient(
                builder,
                "https://api.mercadopago.test",
                "TEST-token-ficticio",
                "https://mismascotas.test/suscripcion/retorno");
    }

    @Test
    void creaPreapprovalPendienteSinPlanAsociado() {
        servidor.expect(requestTo("https://api.mercadopago.test/preapproval"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-token-ficticio"))
                .andExpect(jsonPath("$.reason").value("Plan Premium MisMascotas"))
                .andExpect(jsonPath("$.external_reference").value("uuid-local"))
                .andExpect(jsonPath("$.payer_email").value("qa@example.test"))
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.back_url").value("https://mismascotas.test/suscripcion/retorno"))
                .andExpect(jsonPath("$.auto_recurring.frequency").value(1))
                .andExpect(jsonPath("$.auto_recurring.frequency_type").value("months"))
                .andExpect(jsonPath("$.auto_recurring.transaction_amount").value(2500.00))
                .andExpect(jsonPath("$.auto_recurring.currency_id").value("ARS"))
                .andExpect(jsonPath("$.preapproval_plan_id").doesNotExist())
                .andRespond(withSuccess(
                        "{\"id\":\"preapproval-qa-1\",\"init_point\":\"https://checkout.test/1\",\"status\":\"pending\"}",
                        MediaType.APPLICATION_JSON));

        var respuesta = cliente.crearPreapproval(solicitud());

        assertThat(respuesta.id()).isEqualTo("preapproval-qa-1");
        assertThat(respuesta.initPoint()).isEqualTo("https://checkout.test/1");
        servidor.verify();
    }

    @Test
    void rechazaRespuestaSinIdentificadorRemoto(CapturedOutput salida) {
        servidor.expect(requestTo("https://api.mercadopago.test/preapproval"))
                .andRespond(withSuccess(
                        "{\"init_point\":\"https://checkout.test/1\",\"status\":\"pending\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.crearPreapproval(solicitud()))
                .isInstanceOf(MercadoPagoException.class);
        assertThat(salida.getAll())
                .contains("referencia=uuid-local HTTP=200 respuesta inesperada conId=false conEnlace=true estadoPendiente=true")
                .doesNotContain("https://checkout.test/1");
        servidor.verify();
    }

    @Test
    void registraElRechazoHttpSinExponerElCuerpoNiCredenciales(CapturedOutput salida) {
        servidor.expect(requestTo("https://api.mercadopago.test/preapproval"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message":"Invalid payer_email: qa@example.test",
                                 "detalle":"TEST-token-ficticio"}
                                """));

        assertThatThrownBy(() -> cliente.crearPreapproval(solicitud()))
                .isInstanceOf(MercadoPagoException.class);
        assertThat(salida.getAll())
                .contains("referencia=uuid-local HTTP=400 motivo=correo del pagador")
                .doesNotContain("qa@example.test", "TEST-token-ficticio", "Invalid payer_email");
        servidor.verify();
    }

    @Test
    void consultaPreapprovalExistentePorId() {
        servidor.expect(requestTo("https://api.mercadopago.test/preapproval/preapproval-qa-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer TEST-token-ficticio"))
                .andRespond(withSuccess("""
                        {
                          "id": "preapproval-qa-1",
                          "external_reference": "uuid-local",
                          "status": "pending",
                          "init_point": "https://checkout.test/existente",
                          "auto_recurring": {
                            "frequency": 1,
                            "frequency_type": "months",
                            "transaction_amount": 2500.0,
                            "currency_id": "ARS"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var respuesta = cliente.consultarPreapproval("preapproval-qa-1");

        assertThat(respuesta.id()).isEqualTo("preapproval-qa-1");
        assertThat(respuesta.referenciaExterna()).isEqualTo("uuid-local");
        assertThat(respuesta.estado()).isEqualTo("pending");
        assertThat(respuesta.initPoint()).isEqualTo("https://checkout.test/existente");
        assertThat(respuesta.importe()).isEqualByComparingTo(new BigDecimal("2500.00"));
        assertThat(respuesta.moneda()).isEqualTo("ARS");
        assertThat(respuesta.frecuencia()).isEqualTo(1);
        assertThat(respuesta.tipoFrecuencia()).isEqualTo("months");
        servidor.verify();
    }

    @Test
    void rechazaConsultaIncompleta() {
        servidor.expect(requestTo("https://api.mercadopago.test/preapproval/preapproval-qa-1"))
                .andRespond(withSuccess("""
                        {
                          "id": "preapproval-qa-1",
                          "status": "pending",
                          "init_point": "https://checkout.test/existente",
                          "auto_recurring": {
                            "frequency": 1,
                            "frequency_type": "months",
                            "transaction_amount": 2500.0,
                            "currency_id": "ARS"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.consultarPreapproval("preapproval-qa-1"))
                .isInstanceOf(MercadoPagoException.class);
        servidor.verify();
    }

    @Test
    void traduceErrorHttpDeConsulta() {
        servidor.expect(requestTo("https://api.mercadopago.test/preapproval/preapproval-qa-1"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        assertThatThrownBy(() -> cliente.consultarPreapproval("preapproval-qa-1"))
                .isInstanceOf(MercadoPagoException.class)
                .hasMessage("No se pudo consultar el preapproval");
        servidor.verify();
    }

    @Test
    void cancelaPreapprovalConLaVarianteAceptadaPorElProveedor() {
        servidor.expect(requestTo("https://api.mercadopago.test/preapproval/preapproval-qa-1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer TEST-token-ficticio"))
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andRespond(withSuccess());

        cliente.cancelarPreapproval("preapproval-qa-1");

        servidor.verify();
    }

    @Test
    void traduceErrorHttpDeCancelacion() {
        servidor.expect(requestTo("https://api.mercadopago.test/preapproval/preapproval-qa-1"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> cliente.cancelarPreapproval("preapproval-qa-1"))
                .isInstanceOf(MercadoPagoException.class)
                .hasMessage("No se pudo solicitar la cancelacion del preapproval");
        servidor.verify();
    }

    private SolicitudPreapproval solicitud() {
        return new SolicitudPreapproval(
                "Plan Premium MisMascotas",
                "uuid-local",
                "qa@example.test",
                new BigDecimal("2500.00"),
                "ARS",
                1,
                "months");
    }
    @ParameterizedTest
    @MethodSource("consultasConErroresHttp")
    void conservaCodigoHttpYRetryAfterEnTodasLasConsultas(String consulta, int codigo) {
        servidor.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(withStatus(HttpStatus.valueOf(codigo)).header("Retry-After", "120")
                        .body("detalle privado que no debe registrarse"));
        assertThatThrownBy(() -> ejecutarConsulta(consulta))
                .isInstanceOfSatisfying(MercadoPagoException.class, error -> {
                    assertThat(error.codigoHttp()).isEqualTo(codigo);
                    assertThat(error.retryAfter()).isEqualTo("120");
                    assertThat(error.getMessage()).doesNotContain("detalle privado");
                    var esperado = codigo == 429 ? MercadoPagoException.TipoFallo.LIMITE_SOLICITUDES
                            : codigo == 401 || codigo == 403 ? MercadoPagoException.TipoFallo.CREDENCIALES_INVALIDAS
                            : codigo >= 500 ? MercadoPagoException.TipoFallo.TEMPORAL
                            : MercadoPagoException.TipoFallo.HTTP_INVALIDO;
                    assertThat(error.tipoFallo()).isEqualTo(esperado);
                });
        servidor.verify();
    }

    @Test
    void credencialAusenteSeIdentificaAntesDeConsultar() {
        var sinCredencial = new MercadoPagoClient(RestClient.builder(), "https://api.mercadopago.test", "", "https://retorno.test");
        assertThatThrownBy(() -> sinCredencial.consultarFacturaAutorizada("factura"))
                .isInstanceOfSatisfying(MercadoPagoException.class,
                        error -> assertThat(error.tipoFallo()).isEqualTo(MercadoPagoException.TipoFallo.CREDENCIALES_INVALIDAS));
        servidor.verify();
    }

    @Test
    void facturaConservaElIdDeSuPagoAsociado() {
        servidor.expect(requestTo("https://api.mercadopago.test/authorized_payments/7034059187"))
                .andRespond(withSuccess("""
                        {"id":7034059187,"status":"processed","preapproval_id":"preapproval-qa","external_reference":"referencia",
                         "payment":{"id":190022188364,"status":"approved"}}
                        """, MediaType.APPLICATION_JSON));
        assertThat(cliente.consultarFacturaAutorizada("7034059187").idPago()).isEqualTo("190022188364");
        servidor.verify();
    }

    private void ejecutarConsulta(String consulta) {
        switch (consulta) {
            case "FACTURA" -> cliente.consultarFacturaAutorizada("factura");
            case "SUSCRIPCION" -> cliente.consultarPreapproval("preapproval");
            case "CUOTAS" -> cliente.buscarCuotas("preapproval");
            case "PAGO" -> cliente.consultarPago("pago");
            default -> throw new IllegalArgumentException("Consulta de prueba desconocida");
        }
    }

    static Stream<Arguments> consultasConErroresHttp() {
        return Stream.of("FACTURA", "SUSCRIPCION", "CUOTAS", "PAGO").flatMap(
                consulta -> Stream.of(400, 401, 403, 404, 429, 500, 503).map(codigo -> Arguments.of(consulta, codigo)));
    }

}
