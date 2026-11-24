package com.MisMascotas.backend.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import com.MisMascotas.backend.Integration.MercadoPagoFirmaWebhook;
import com.MisMascotas.backend.Repository.NotificacionMercadoPagoRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class MercadoPagoWebhookService {

    private static final java.util.Set<String> TOPICOS = java.util.Set.of("subscription_authorized_payment", "subscription_preapproval");
    private final MercadoPagoFirmaWebhook firma = new MercadoPagoFirmaWebhook();
    private final JsonMapper json;
    private final NotificacionMercadoPagoRepository notificaciones;
    private final TransactionTemplate transaccion;
    private final String secreto;

    public MercadoPagoWebhookService(JsonMapper json, NotificacionMercadoPagoRepository notificaciones,
            PlatformTransactionManager transacciones,
            @Value("${mercadopago.webhook.secret:}") String secreto) {
        this.json = json;
        this.notificaciones = notificaciones;
        this.secreto = secreto;
        this.transaccion = new TransactionTemplate(transacciones);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ResultadoRecepcion recibir(String dataId, String requestId, String signature, String cuerpo) {
        if (secreto == null || secreto.isBlank()) {
            return ResultadoRecepcion.NO_DISPONIBLE;
        }
        if (!firma.esValida(dataId, requestId, signature, secreto)) {
            return ResultadoRecepcion.FIRMA_INVALIDA;
        }

        JsonNode notificacion;
        try {
            notificacion = cuerpo == null ? null : json.readTree(cuerpo);
        } catch (JacksonException ex) {
            return ResultadoRecepcion.DATOS_INVALIDOS;
        }
        if (notificacion == null || !notificacion.isObject()) {
            return ResultadoRecepcion.DATOS_INVALIDOS;
        }
        JsonNode tipo = notificacion.path("type");
        if (!tipo.isString() || tipo.asString().isBlank()) {
            return ResultadoRecepcion.DATOS_INVALIDOS;
        }
        if (!TOPICOS.contains(tipo.asString())) {
            return ResultadoRecepcion.ACEPTADA;
        }
        String idNotificacion = id(notificacion.path("id"));
        String idRecurso = id(notificacion.path("data").path("id"));
        if (idNotificacion == null || idRecurso == null || !idRecurso.equals(dataId)) {
            return ResultadoRecepcion.DATOS_INVALIDOS;
        }

        try {
            // execute termina el commit antes de permitir el acuse HTTP, incluso en un reenvio.
            transaccion.execute(estado -> notificaciones.registrar(idNotificacion, tipo.asString(), idRecurso));
            return ResultadoRecepcion.ACEPTADA;
        } catch (DataAccessException | TransactionException ex) {
            // No registrar cabeceras, cuerpo ni secreto. El proveedor debe reenviar la notificacion.
            return ResultadoRecepcion.NO_DISPONIBLE;
        }
    }

    private String id(JsonNode valor) {
        if (!valor.isString() && !valor.isIntegralNumber()) {
            return null;
        }
        String texto = valor.isString() ? valor.asString() : valor.toString();
        return texto.isBlank() ? null : texto;
    }

    public enum ResultadoRecepcion {
        ACEPTADA, FIRMA_INVALIDA, DATOS_INVALIDOS, NO_DISPONIBLE
    }
}
