package com.MisMascotas.backend.Integration;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.MisMascotas.backend.Exception.MercadoPagoException;

@Component
public class MercadoPagoClient implements MercadoPagoGateway {

    private static final Logger logger = LoggerFactory.getLogger(MercadoPagoClient.class);
    private static final String ESTADO_PENDIENTE = "pending";
    private static final ObjectMapper JSON_PAGOS = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private final RestClient restClient;
    private final String accessToken;
    private final String backUrl;

    public MercadoPagoClient(
            RestClient.Builder restClientBuilder,
            @Value("${mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
            @Value("${mercadopago.access-token:}") String accessToken,
            @Value("${mercadopago.back-url:}") String backUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.accessToken = accessToken;
        this.backUrl = backUrl;
    }

    @Override
    public void validarConfiguracion() {
        validarAccessToken();
        if (backUrl == null || backUrl.isBlank()) {
            throw new IllegalStateException("La URL de retorno de Mercado Pago no esta configurada");
        }
    }

    private void validarAccessToken() {
        if (accessToken == null || accessToken.isBlank()) {
            throw MercadoPagoException.credencialesAusentes();
        }
    }

    @Override
    public PreapprovalCreado crearPreapproval(SolicitudPreapproval solicitud) {
        validarConfiguracion();
        validarSolicitud(solicitud);

        PreapprovalRequest cuerpo = new PreapprovalRequest(
                solicitud.motivo(),
                solicitud.referenciaExterna(),
                solicitud.emailPagador(),
                new AutoRecurringRequest(
                        solicitud.frecuencia(),
                        solicitud.tipoFrecuencia(),
                        solicitud.importe(),
                        solicitud.moneda()),
                backUrl,
                ESTADO_PENDIENTE);

        try {
            ResponseEntity<PreapprovalResponse> respuestaHttp = restClient.post()
                    .uri("/preapproval")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .toEntity(PreapprovalResponse.class);
            PreapprovalResponse respuesta = respuestaHttp.getBody();
            boolean tieneId = respuesta != null && !estaEnBlanco(respuesta.id());
            boolean tieneEnlace = respuesta != null && !estaEnBlanco(respuesta.init_point());
            boolean estadoPendiente = respuesta != null && ESTADO_PENDIENTE.equalsIgnoreCase(respuesta.status());

            if (!tieneId || !tieneEnlace || !estadoPendiente) {
                logger.warn("Mercado Pago POST /preapproval referencia={} HTTP={} respuesta inesperada "
                                + "conId={} conEnlace={} estadoPendiente={}",
                        solicitud.referenciaExterna(), respuestaHttp.getStatusCode().value(),
                        tieneId, tieneEnlace, estadoPendiente);
                throw new MercadoPagoException("Mercado Pago devolvio una respuesta incompleta o inesperada");
            }

            return new PreapprovalCreado(respuesta.id(), respuesta.init_point());
        } catch (RestClientResponseException ex) {
            logger.warn("Mercado Pago POST /preapproval referencia={} HTTP={} motivo={}",
                    solicitud.referenciaExterna(), ex.getStatusCode().value(), motivoSeguroPreapproval(ex));
            throw new MercadoPagoException("No se pudo confirmar la creacion del preapproval", ex);
        } catch (ResourceAccessException ex) {
            logger.warn("Mercado Pago POST /preapproval referencia={} fallo de conexion causa={}",
                    solicitud.referenciaExterna(), ex.getMostSpecificCause().getClass().getSimpleName());
            throw new MercadoPagoException("No se pudo confirmar la creacion del preapproval", ex);
        } catch (RestClientException ex) {
            logger.warn("Mercado Pago POST /preapproval referencia={} fallo al procesar la respuesta causa={}",
                    solicitud.referenciaExterna(), ex.getMostSpecificCause().getClass().getSimpleName());
            throw new MercadoPagoException("No se pudo confirmar la creacion del preapproval", ex);
        }
    }

    private String motivoSeguroPreapproval(RestClientResponseException ex) {
        // Clasifica el error con mensajes fijos para mantener fuera del log los datos del proveedor.
        return switch (ex.getStatusCode().value()) {
            case 401, 403 -> "credenciales o permisos";
            case 429 -> "limite de solicitudes";
            case 400, 422 -> {
                String detalle = ex.getResponseBodyAsString().toLowerCase(Locale.ROOT);
                if (detalle.contains("payer") && detalle.contains("collector")) {
                    yield "cuentas del pagador y el vendedor";
                }
                if (detalle.contains("payer_email")) {
                    yield "correo del pagador";
                }
                if (detalle.contains("back_url")) {
                    yield "URL de retorno";
                }
                if (detalle.contains("transaction_amount") || detalle.contains("currency_id")) {
                    yield "importe o moneda";
                }
                yield "solicitud rechazada";
            }
            default -> "error del proveedor";
        };
    }

    @Override
    public PreapprovalConsultado consultarPreapproval(String idPreapproval) {
        validarAccessToken();
        if (estaEnBlanco(idPreapproval)) {
            throw new IllegalArgumentException("El identificador del preapproval es obligatorio");
        }

        try {
            PreapprovalConsultaResponse respuesta = restClient.get()
                    .uri("/preapproval/{id}", idPreapproval)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(PreapprovalConsultaResponse.class);

            if (respuesta == null
                    || estaEnBlanco(respuesta.id())
                    || estaEnBlanco(respuesta.external_reference())
                    || estaEnBlanco(respuesta.status())
                    || ("pending".equalsIgnoreCase(respuesta.status()) && estaEnBlanco(respuesta.init_point()))
                    || respuesta.auto_recurring() == null
                    || respuesta.auto_recurring().transaction_amount() == null
                    || estaEnBlanco(respuesta.auto_recurring().currency_id())
                    || respuesta.auto_recurring().frequency() <= 0
                    || estaEnBlanco(respuesta.auto_recurring().frequency_type())) {
                throw new MercadoPagoException("Mercado Pago devolvio una consulta incompleta");
            }

            return new PreapprovalConsultado(
                    respuesta.id(),
                    respuesta.external_reference(),
                    respuesta.status(),
                    respuesta.init_point(),
                    respuesta.auto_recurring().transaction_amount(),
                    respuesta.auto_recurring().currency_id(),
                    respuesta.auto_recurring().frequency(),
                    respuesta.auto_recurring().frequency_type(),
                    fechaOpcional(respuesta.next_payment_date()), fechaOpcional(respuesta.last_modified()));
        } catch (MercadoPagoException ex) {
            throw ex;
        } catch (RestClientException | java.time.format.DateTimeParseException ex) {
            throw new MercadoPagoException("No se pudo consultar el preapproval", ex);
        }
    }

    @Override
    public void cancelarPreapproval(String idPreapproval) {
        validarAccessToken();
        if (estaEnBlanco(idPreapproval)) {
            throw new IllegalArgumentException("El identificador del preapproval es obligatorio");
        }

        try {
            restClient.put()
                    .uri("/preapproval/{id}", idPreapproval)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ActualizacionEstadoRequest("cancelled"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new MercadoPagoException("No se pudo solicitar la cancelacion del preapproval", ex);
        }
    }

    private void validarSolicitud(SolicitudPreapproval solicitud) {
        if (solicitud == null
                || estaEnBlanco(solicitud.motivo())
                || estaEnBlanco(solicitud.referenciaExterna())
                || estaEnBlanco(solicitud.emailPagador())
                || solicitud.importe() == null
                || solicitud.importe().signum() <= 0
                || estaEnBlanco(solicitud.moneda())
                || solicitud.frecuencia() <= 0
                || estaEnBlanco(solicitud.tipoFrecuencia())) {
            throw new IllegalArgumentException("La solicitud de preapproval no esta completa");
        }
    }

    @Override
    public List<CuotaConsultada> buscarCuotas(String idPreapproval) {
        validarAccessToken();
        if (estaEnBlanco(idPreapproval)) {
            throw new IllegalArgumentException("El identificador del preapproval es obligatorio");
        }
        List<CuotaConsultada> cuotas = new ArrayList<>();
        int offset = 0;
        Integer totalEsperado = null;
        try {
            // Sin un conjunto completo y estable no se puede elegir el primer cobro.
            while (true) {
                int desplazamiento = offset;
                String cuerpo = restClient.get()
                        .uri(uri -> uri.path("/authorized_payments/search")
                                .queryParam("preapproval_id", idPreapproval)
                                .queryParam("offset", desplazamiento).build())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .retrieve().body(String.class);
                JsonNode respuesta = cuerpo == null ? null : JSON_PAGOS.readTree(cuerpo);
                if (respuesta == null || !respuesta.path("results").isArray()
                        || !respuesta.path("paging").path("total").isIntegralNumber()
                        || !respuesta.path("paging").path("total").canConvertToInt()
                        || !respuesta.path("paging").path("offset").isIntegralNumber()
                        || !respuesta.path("paging").path("offset").canConvertToInt()) {
                    throw new MercadoPagoException("La busqueda de cuotas esta incompleta");
                }
                int total = respuesta.path("paging").path("total").intValue();
                JsonNode resultados = respuesta.path("results");
                if (total < 0 || total > 10000
                        || respuesta.path("paging").path("offset").intValue() != offset
                        || (totalEsperado != null && totalEsperado != total)
                        || (resultados.isEmpty() && offset < total)) {
                    throw new MercadoPagoException("No se pudo verificar la paginacion de cuotas");
                }
                totalEsperado = total;
                for (JsonNode cuota : resultados) {
                    String remoto = texto(cuota, "preapproval_id");
                    if (!idPreapproval.equals(remoto)) {
                        throw new MercadoPagoException("La cuota no corresponde al preapproval consultado");
                    }
                    // Las cuotas aun no cobradas pueden no tener payment.id.
                    String idPago = texto(cuota.path("payment"), "id");
                    if (cuota.hasNonNull("payment") && idPago == null) {
                        throw new MercadoPagoException("La cuota incluye un pago sin identificador verificable");
                    }
                    cuotas.add(new CuotaConsultada(remoto, idPago));
                }
                offset += resultados.size();
                if (offset > total) {
                    throw new MercadoPagoException("La cantidad de cuotas no coincide con la paginacion");
                }
                if (offset == total) {
                    return List.copyOf(cuotas);
                }
            }
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new MercadoPagoException("No se pudo consultar las cuotas", ex);
        }
    }

    @Override
    public PagoConsultado consultarPago(String idPago) {
        validarAccessToken();
        if (estaEnBlanco(idPago)) {
            throw new IllegalArgumentException("El identificador del pago es obligatorio");
        }
        try {
            String cuerpo = restClient.get().uri("/v1/payments/{id}", idPago)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().body(String.class);
            JsonNode respuesta = cuerpo == null ? null : JSON_PAGOS.readTree(cuerpo);
            if (respuesta == null || texto(respuesta, "id") == null || texto(respuesta, "status") == null) {
                throw new MercadoPagoException("El detalle del pago esta incompleto");
            }
            String fecha = texto(respuesta, "date_approved");
            return new PagoConsultado(
                    texto(respuesta, "id"), texto(respuesta, "status"),
                    importe(respuesta.path("transaction_amount")),
                    texto(respuesta, "currency_id"),
                    fecha == null ? null : OffsetDateTime.parse(fecha).toInstant(), texto(respuesta, "status_detail"));
        } catch (RestClientException | java.time.format.DateTimeParseException
                | com.fasterxml.jackson.core.JsonProcessingException | NumberFormatException ex) {
            throw new MercadoPagoException("No se pudo verificar el detalle del pago", ex);
        }
    }

    @Override
    public FacturaAutorizada consultarFacturaAutorizada(String idFactura) {
        validarAccessToken();
        if (estaEnBlanco(idFactura)) {
            throw new IllegalArgumentException("El identificador de la factura es obligatorio");
        }
        try {
            String cuerpo = restClient.get().uri("/authorized_payments/{id}", idFactura)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().body(String.class);
            JsonNode respuesta = cuerpo == null ? null : JSON_PAGOS.readTree(cuerpo);
            String id = respuesta == null ? null : texto(respuesta, "id");
            String preapproval = respuesta == null ? null : texto(respuesta, "preapproval_id");
            String referencia = respuesta == null ? null : texto(respuesta, "external_reference");
            if (id == null || preapproval == null || referencia == null) {
                throw new MercadoPagoException("La consulta de la factura esta incompleta");
            }
            String idPago = texto(respuesta.path("payment"), "id");
            if (respuesta.hasNonNull("payment") && idPago == null) {
                throw new MercadoPagoException("La factura incluye un pago sin identificador verificable");
            }
            String estado = texto(respuesta, "status");
            if (estado == null) throw new MercadoPagoException("La factura no incluye estado");
            JsonNode intento = respuesta.path("retry_attempt");
            if (!intento.isMissingNode() && !intento.isNull()
                    && (!intento.isIntegralNumber() || !intento.canConvertToInt() || intento.intValue() < 0)) {
                throw new MercadoPagoException("La factura incluye un intento invalido");
            }
            return new FacturaAutorizada(id, preapproval, referencia, idPago, estado,
                    intento.isIntegralNumber() ? intento.intValue() : null,
                    fechaOpcional(texto(respuesta, "debit_date")),
                    fechaOpcional(texto(respuesta, "next_retry_date")),
                    importe(respuesta.path("transaction_amount")), texto(respuesta, "currency_id"));
        } catch (MercadoPagoException ex) {
            throw ex;
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException
                | java.time.format.DateTimeParseException | NumberFormatException ex) {
            throw new MercadoPagoException("No se pudo consultar la factura autorizada", ex);
        }
    }

    private java.time.Instant fechaOpcional(String fecha) {
        return fecha == null ? null : OffsetDateTime.parse(fecha).toInstant();
    }

    private BigDecimal importe(JsonNode valor) {
        return valor.isNumber() || valor.isTextual() ? new BigDecimal(valor.asText()) : null;
    }

    private String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.path(campo);
        if (!valor.isTextual() && !valor.isIntegralNumber()) {
            return null;
        }
        String texto = valor.asText();
        return texto.isBlank() ? null : texto;
    }

    private boolean estaEnBlanco(String valor) {
        return valor == null || valor.isBlank();
    }

    private record PreapprovalRequest(
            String reason,
            String external_reference,
            String payer_email,
            AutoRecurringRequest auto_recurring,
            String back_url,
            String status) {
    }

    private record AutoRecurringRequest(
            int frequency,
            String frequency_type,
            BigDecimal transaction_amount,
            String currency_id) {
    }

    private record PreapprovalResponse(String id, String init_point, String status) {
    }

    private record PreapprovalConsultaResponse(
            String id,
            String external_reference,
            String status,
            String init_point,
            AutoRecurringResponse auto_recurring, String next_payment_date, String last_modified) {
    }

    private record AutoRecurringResponse(
            int frequency,
            String frequency_type,
            BigDecimal transaction_amount,
            String currency_id) {
    }

    private record ActualizacionEstadoRequest(String status) {
    }
}
