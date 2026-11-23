package com.MisMascotas.backend.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.MisMascotas.backend.Service.MercadoPagoWebhookService;

@RestController
public class MercadoPagoWebhookController {

    private final MercadoPagoWebhookService recepcion;

    public MercadoPagoWebhookController(MercadoPagoWebhookService recepcion) {
        this.recepcion = recepcion;
    }

    @PostMapping("/webhooks/mercado-pago")
    public ResponseEntity<Void> recibir(
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestHeader(name = "x-signature", required = false) String signature,
            @RequestBody(required = false) String cuerpo) {
        // El JSON se interpreta despues de verificar la firma, no durante el binding de MVC.
        int estado = switch (recepcion.recibir(dataId, requestId, signature, cuerpo)) {
            case ACEPTADA -> 200;
            case FIRMA_INVALIDA -> 401;
            case DATOS_INVALIDOS -> 400;
            case NO_DISPONIBLE -> 503;
        };
        return ResponseEntity.status(estado).build();
    }
}
