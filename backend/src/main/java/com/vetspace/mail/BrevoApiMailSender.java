package com.vetspace.mail;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Brevo delivery over its HTTP API instead of SMTP, enabled via
 * {@code app.mail.mode=brevo-api}.
 *
 * <p>Why this exists: Railway (and several other PaaS free tiers) block outbound SMTP
 * entirely — ports 25, 465 and 587 all black-hole, so a perfectly correct
 * {@code smtp-relay.brevo.com:587} configuration can never work from there. The HTTP API
 * runs on 443, which nothing blocks. Same Brevo account, same verified sender; only the
 * transport differs.
 *
 * <p>Failures are rethrown as {@link MailSendException} so the callers' existing
 * {@code catch (MailException)} handling — non-fatal sends, {@code verificationEmailSent}
 * flags, ERROR logs without addresses or tokens — applies unchanged.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "mode", havingValue = "brevo-api")
public class BrevoApiMailSender implements AppMailSender {

    static final String DEFAULT_BASE_URL = "https://api.brevo.com";

    private final RestClient restClient;
    private final String from;

    public BrevoApiMailSender(RestClient.Builder restClientBuilder,
                              @Value("${app.mail.from}") String from,
                              @Value("${app.mail.brevo-api-key}") String apiKey,
                              @Value("${app.mail.brevo-base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
                              @Value("${spring.mail.properties.mail.smtp.connectiontimeout:5000}") int timeoutMs) {
        // Same bounded-time guarantee as the SMTP path: a silent endpoint delays the
        // registration response by at most the timeout, never indefinitely. A
        // non-positive timeout skips the explicit factory — the unit tests use that to
        // keep MockRestServiceServer's bound transport (a factory set here would
        // override it and send the test traffic to the real API).
        if (timeoutMs > 0) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
            requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
            restClientBuilder = restClientBuilder.requestFactory(requestFactory);
        }
        this.restClient = restClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("api-key", apiKey)
            .build();
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        send(to, subject, body, null);
    }

    @Override
    public void send(String to, String subject, String body, String replyTo) {
        Map<String, Object> payload = replyTo == null || replyTo.isBlank()
            ? Map.of(
                "sender", Map.of("email", from),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", body)
            : Map.of(
                "sender", Map.of("email", from),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", body,
                "replyTo", Map.of("email", replyTo));
        try {
            restClient.post()
                .uri("/v3/smtp/email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        } catch (RuntimeException ex) {
            // Wrapped so callers keep their single catch (MailException). The cause's own
            // message may quote Brevo's error body; it never contains the api key — the
            // key travels only in the request header.
            throw new MailSendException("Brevo API send failed", ex);
        }
    }
}
