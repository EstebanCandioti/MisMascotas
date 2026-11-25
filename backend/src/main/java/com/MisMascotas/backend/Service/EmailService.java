package com.MisMascotas.backend.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    /** El listener llama a este método fuera de la transacción que reserva o concede acceso. */
    public boolean enviarNotificacionCuidado(NotificacionCuidado aviso, String enlace) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            logger.warn("SMTP no configurado para la notificacion de cuidado {}", aviso.invitacionId());
            return false;
        }
        try {
            var mensaje = new SimpleMailMessage();
            mensaje.setTo(aviso.emailDestino());
            if (aviso.tipo() == NotificacionCuidado.Tipo.INVITACION) {
                mensaje.setSubject("Invitacion de cuidado - MisMascotas");
                mensaje.setText(aviso.nombrePropietario() + " te invito a compartir el cuidado de sus mascotas con el perfil "
                        + aviso.perfil() + ".\nLa invitacion vence el " + aviso.expiraEn()
                        + ".\n\nVer invitacion: " + enlace
                        + "\n\nInicia sesion con tu cuenta para aceptar o rechazar desde la web. Abrir este enlace no concede acceso.");
            } else {
                mensaje.setSubject("Respuesta a tu invitacion - MisMascotas");
                mensaje.setText(aviso.nombreInvitado() + (aviso.tipo() == NotificacionCuidado.Tipo.ACEPTADA ? " acepto" : " rechazo")
                        + " tu invitacion de cuidado compartido.\n\nVer detalle: " + enlace);
            }
            sender.send(mensaje);
            return true;
        } catch (Exception ex) {
            logger.warn("Fallo SMTP de cuidado para invitacion {}: {}", aviso.invitacionId(), ex.getClass().getSimpleName());
            return false;
        }
    }

    @Async
    public void enviarCodigoVerificacion(String email, String codigo) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            logger.warn("SMTP no configurado. No se pudo enviar el codigo de verificacion por email.");
            return;
        }

        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(email);
            mensaje.setSubject("Codigo de verificacion - MisMascotas");
            mensaje.setText("Tu codigo de verificacion es: " + codigo);
            mailSender.send(mensaje);
        } catch (Exception ex) {
            logger.error("No se pudo enviar el codigo de verificacion por email", ex);
        }
    }
}
