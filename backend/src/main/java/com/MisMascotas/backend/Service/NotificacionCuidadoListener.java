package com.MisMascotas.backend.Service;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class NotificacionCuidadoListener {
    private static final Logger log = LoggerFactory.getLogger(NotificacionCuidadoListener.class);
    private final EmailService correo;
    private final String urlInvitaciones;

    public NotificacionCuidadoListener(EmailService correo,
            @Value("${cuidado-compartido.url-invitaciones:http://localhost:5173/cuidado-compartido}") String url) {
        var uri = URI.create(url);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
            throw new IllegalArgumentException("La URL web de invitaciones debe ser HTTP/HTTPS absoluta, sin credenciales, consulta ni fragmento");
        this.correo = correo;
        this.urlInvitaciones = url;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviar(NotificacionCuidado aviso) {
        try {
            String enlace = UriComponentsBuilder.fromUriString(urlInvitaciones)
                    .queryParam("invitacionId", aviso.invitacionId()).build().toUriString();
            correo.enviarNotificacionCuidado(aviso, enlace);
        } catch (RuntimeException ex) {
            log.warn("No se pudo notificar la invitacion {} despues del commit: {}", aviso.invitacionId(), ex.getClass().getSimpleName());
        }
    }
}
