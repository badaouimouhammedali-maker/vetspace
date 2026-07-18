package com.vetspace.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default in dev/tests: logs instead of sending, so local runs and the test suite don't need a working SMTP server. */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "mode", havingValue = "log", matchIfMissing = true)
public class LoggingMailSender implements AppMailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[log-only mail] to={} subject={} body={}", to, subject, body);
    }

    @Override
    public void send(String to, String subject, String body, String replyTo) {
        log.info("[log-only mail] to={} replyTo={} subject={} body={}", to, replyTo, subject, body);
    }
}
