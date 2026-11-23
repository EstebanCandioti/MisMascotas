package com.MisMascotas.backend.Integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.MisMascotas.backend.Exception.MercadoPagoException;

class MercadoPagoCicloClientTests {
    MercadoPagoClient cliente; MockRestServiceServer servidor;
    @BeforeEach void preparar(){var builder=RestClient.builder();servidor=MockRestServiceServer.bindTo(builder).build();cliente=new MercadoPagoClient(builder,"https://mp.test","credencial-ficticia","https://retorno.test");}
    @Test void facturaRecyclingRealSinFechaDeReintentoSeLeeCompleta(){
        responderFactura("recycling", "");
        var f=cliente.consultarFacturaAutorizada("7034112077");
        assertThat(f.estado()).isEqualTo("recycling");assertThat(f.intento()).isEqualTo(1);
        assertThat(f.proximoReintento()).isNull();assertThat(f.fechaDebito()).isEqualTo(Instant.parse("2026-11-18T20:18:22Z"));
        assertThat(f.idPago()).isEqualTo("190384610876");assertThat(f.importe()).isEqualByComparingTo("4000");servidor.verify();
    }
    @Test void proximoReintentoOpcionalSeConservaSinCalcularNada(){
        responderFactura("recycling", ",\"next_retry_date\":\"2026-11-20T17:18:22-03:00\"");
        assertThat(cliente.consultarFacturaAutorizada("7034112077").proximoReintento()).isEqualTo(Instant.parse("2026-11-20T20:18:22Z"));servidor.verify();
    }
    @ParameterizedTest @ValueSource(strings={"-1", "1.5", "\"invalido\"", "99999999999999999999"})
    void contadorInvalidoRequiereRevision(String intento){
        servidor.expect(requestTo("https://mp.test/authorized_payments/7034112077")).andRespond(withSuccess(
            "{\"id\":7034112077,\"preapproval_id\":\"s\",\"external_reference\":\"r\",\"status\":\"recycling\",\"retry_attempt\":"+intento+"}",MediaType.APPLICATION_JSON));
        assertThatThrownBy(()->cliente.consultarFacturaAutorizada("7034112077")).isInstanceOfSatisfying(MercadoPagoException.class,e->assertThat(e.tipoFallo()).isEqualTo(MercadoPagoException.TipoFallo.RESPUESTA_INVALIDA));
    }
    @ParameterizedTest @ValueSource(strings={"debit_date","next_retry_date"})
    void fechaInformadaPeroInvalidaNoSeInventa(String campo){
        responderFactura("recycling", ",\""+campo+"\":\"fecha-invalida\"");
        assertThatThrownBy(()->cliente.consultarFacturaAutorizada("7034112077")).isInstanceOf(MercadoPagoException.class);
    }
    @Test void leeFechaDelSiguienteCobroDeLaSuscripcion(){
        servidor.expect(requestTo("https://mp.test/preapproval/s")).andRespond(withSuccess("""
            {"id":"s","external_reference":"r","status":"authorized",
             "auto_recurring":{"frequency":1,"frequency_type":"months","transaction_amount":4000,"currency_id":"ARS"},
             "next_payment_date":"2026-12-18T17:25:17-03:00","last_modified":"2026-11-18T17:25:23.143-03:00"}
            """,MediaType.APPLICATION_JSON));
        var s=cliente.consultarPreapproval("s");assertThat(s.proximoPago()).isEqualTo(Instant.parse("2026-12-18T20:25:17Z"));assertThat(s.ultimaModificacion()).isEqualTo(Instant.parse("2026-11-18T20:25:23.143Z"));servidor.verify();
    }
    @Test void pagoConservaMotivoDelRechazo(){
        servidor.expect(requestTo("https://mp.test/v1/payments/p")).andRespond(withSuccess("""
            {"id":"p","status":"rejected","status_detail":"cc_rejected_other_reason","transaction_amount":4000,"currency_id":"ARS","date_approved":null}
            """,MediaType.APPLICATION_JSON));
        assertThat(cliente.consultarPago("p").detalleEstado()).isEqualTo("cc_rejected_other_reason");servidor.verify();
    }
    void responderFactura(String estado,String extra){
        servidor.expect(requestTo("https://mp.test/authorized_payments/7034112077")).andExpect(method(HttpMethod.GET))
          .andRespond(withSuccess("{\"id\":7034112077,\"preapproval_id\":\"s\",\"external_reference\":\"r\",\"status\":\""+estado+"\",\"retry_attempt\":1,\"debit_date\":\"2026-11-18T17:18:22-03:00\",\"transaction_amount\":4000,\"currency_id\":\"ARS\",\"payment\":{\"id\":190384610876}"+extra+"}",MediaType.APPLICATION_JSON));
    }
}
