package com.MisMascotas.backend.Exception;

import java.time.format.DateTimeParseException;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;

public class MercadoPagoException extends RuntimeException {

    private final TipoFallo tipoFallo;
    private final Integer codigoHttp;
    private final String retryAfter;

    /** Una respuesta recibida que no satisface el contrato requiere revision. */
    public MercadoPagoException(String mensaje) {
        this(mensaje, null, TipoFallo.RESPUESTA_INVALIDA, null, null);
    }

    public MercadoPagoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
        if (causa instanceof MercadoPagoException fallo) {
            tipoFallo = fallo.tipoFallo();
            codigoHttp = fallo.codigoHttp();
            retryAfter = fallo.retryAfter();
        } else if (causa instanceof RestClientResponseException respuesta) {
            codigoHttp = respuesta.getStatusCode().value();
            retryAfter = respuesta.getResponseHeaders() == null
                    ? null : respuesta.getResponseHeaders().getFirst("Retry-After");
            tipoFallo = clasificarHttp(codigoHttp);
        } else {
            codigoHttp = null;
            retryAfter = null;
            tipoFallo = clasificarCausa(causa);
        }
    }

    private MercadoPagoException(String mensaje, Throwable causa, TipoFallo tipoFallo,
            Integer codigoHttp, String retryAfter) {
        super(mensaje, causa);
        this.tipoFallo = tipoFallo;
        this.codigoHttp = codigoHttp;
        this.retryAfter = retryAfter;
    }

    public static MercadoPagoException credencialesAusentes() {
        return new MercadoPagoException("El Access Token de Mercado Pago no esta configurado", null,
                TipoFallo.CREDENCIALES_INVALIDAS, null, null);
    }

    public TipoFallo tipoFallo() {
        return tipoFallo;
    }

    public Integer codigoHttp() {
        return codigoHttp;
    }

    public String retryAfter() {
        return retryAfter;
    }

    private static TipoFallo clasificarHttp(int codigo) {
        if (codigo == 429) {
            return TipoFallo.LIMITE_SOLICITUDES;
        }
        if (codigo == 401 || codigo == 403) {
            return TipoFallo.CREDENCIALES_INVALIDAS;
        }
        return codigo >= 500 ? TipoFallo.TEMPORAL : TipoFallo.HTTP_INVALIDO;
    }

    private static TipoFallo clasificarCausa(Throwable causa) {
        if (causa instanceof ResourceAccessException) {
            return TipoFallo.TEMPORAL;
        }
        if (causa instanceof RestClientException || causa instanceof JsonProcessingException
                || causa instanceof DateTimeParseException || causa instanceof IllegalArgumentException) {
            return TipoFallo.RESPUESTA_INVALIDA;
        }
        return TipoFallo.TEMPORAL;
    }

    public enum TipoFallo {
        TEMPORAL, LIMITE_SOLICITUDES, CREDENCIALES_INVALIDAS, HTTP_INVALIDO, RESPUESTA_INVALIDA
    }
}
