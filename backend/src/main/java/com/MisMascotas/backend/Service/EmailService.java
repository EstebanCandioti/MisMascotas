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