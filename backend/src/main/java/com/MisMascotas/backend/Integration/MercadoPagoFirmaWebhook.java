package com.MisMascotas.backend.Integration;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class MercadoPagoFirmaWebhook {

    /**
     * Valida el data.id recibido en la URL. No verifica la antiguedad del timestamp.
     * Las entradas ausentes o mal formadas se rechazan sin registrar datos sensibles.
     */
    public boolean esValida(String dataId, String requestId, String signature, String secreto) {
        if (ausente(dataId) || ausente(requestId) || ausente(signature) || ausente(secreto)
                || signature.indexOf('\r') >= 0 || signature.indexOf('\n') >= 0) {
            return false;
        }

        String timestamp = null;
        String firmaHexadecimal = null;
        for (String campo : signature.split(",", -1)) {
            String[] partes = campo.split("=", 2);
            if (partes.length != 2) {
                return false;
            }
            String nombre = partes[0].trim();
            String valor = partes[1].trim();
            switch (nombre) {
                case "ts" -> {
                    if (timestamp != null) {
                        return false;
                    }
                    timestamp = valor;
                }
                case "v1" -> {
                    if (firmaHexadecimal != null) {
                        return false;
                    }
                    firmaHexadecimal = valor;
                }
                default -> {
                    return false;
                }
            }
        }
        if (timestamp == null || !timestamp.matches("[0-9]+")
                || firmaHexadecimal == null || firmaHexadecimal.length() != 64) {
            return false;
        }

        byte[] firmaRecibida;
        try {
            firmaRecibida = HexFormat.of().parseHex(firmaHexadecimal);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        // Conserva los textos exactos: no normaliza IDs, secreto ni ceros del timestamp.
        String mensaje = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] firmaCalculada = hmac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(firmaCalculada, firmaRecibida);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo calcular la firma del webhook", ex);
        }
    }

    private boolean ausente(String valor) {
        return valor == null || valor.isBlank();
    }
}
