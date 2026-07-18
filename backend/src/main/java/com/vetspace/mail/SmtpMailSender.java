package com.vetspace.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Real spring-mail delivery, enabled via app.mail.mode=smtp once a working SMTP server/creds are configured. */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "mode", havingValue = "smtp")
public class SmtpMailSender implements AppMailSender {

    private final JavaMailSender javaMailSender;
    private final String from;

    public SmtpMailSender(JavaMailSender javaMailSender, @Value("${app.mail.from}") String from) {
        this.javaMailSender = javaMailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        send(to, subject, body, null);
    }

    @Override
    public void send(String to, String subject, String body, String replyTo) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        if (replyTo != null && !replyTo.isBlank()) {
            message.setReplyTo(replyTo);
        }
        javaMailSender.send(message);
    }
}
