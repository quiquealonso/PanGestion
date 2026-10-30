package com.pangestion.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Emails a los clientes por SMTP (Gmail con contrasena de aplicacion, Brevo, Mailjet...): variables SMTP_* del .env.
 * Sin SMTP configurado no se envia nada: las cuentas se activan sin confirmar el email (ver CustomerAccountService).
 */
@Slf4j
@Service
public class MailService {

    private final JavaMailSender sender;
    private final String host;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> sender,
                       @Value("${spring.mail.host:}") String host,
                       @Value("${app.mail.from:}") String from) {
        this.sender = sender.getIfAvailable();
        this.host = host;
        this.from = from;
        if (!isEnabled()) {
            log.warn("Sin SMTP (SMTP_HOST): no se envian emails y las cuentas de cliente se activan sin confirmar el email");
        }
    }

    public boolean isEnabled() {
        return sender != null && StringUtils.hasText(host);
    }

    /** Envia un email de texto. Devuelve false si no se pudo (se registra en el log; el cliente puede pedir otro). */
    public boolean send(String to, String subject, String text) {
        if (!isEnabled()) {
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(StringUtils.hasText(from) ? from : "PanGestion <no-reply@pangestion.local>");
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
            return true;
        } catch (MailException e) {
            log.error("No se pudo enviar el email a {}: {}", to, e.getMessage());
            return false;
        }
    }
}
