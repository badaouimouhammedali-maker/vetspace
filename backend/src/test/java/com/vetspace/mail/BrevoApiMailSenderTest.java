package com.vetspace.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The transport used when the platform blocks outbound SMTP (Railway's free tier
 * black-holes ports 25/465/587). What matters here: the request carries the key in a
 * header and the recipient in the body, and every failure surfaces as a
 * {@link MailSendException} so the callers' non-fatal handling applies unchanged.
 */
class BrevoApiMailSenderTest {

    private static final String API_KEY = "xkeysib-test-key";

    private MockRestServiceServer server;
    private BrevoApiMailSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        // timeout 0: keep the mock server's transport — see the constructor comment.
        sender = new BrevoApiMailSender(builder, "noreply@vetspace.dz", API_KEY,
            BrevoApiMailSender.DEFAULT_BASE_URL, 0);
    }

    @Test
    void sendsTheExpectedRequest() {
        server.expect(requestTo(BrevoApiMailSender.DEFAULT_BASE_URL + "/v3/smtp/email"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("api-key", API_KEY))
            .andExpect(jsonPath("$.sender.email").value("noreply@vetspace.dz"))
            .andExpect(jsonPath("$.to[0].email").value("student@vetspace.dz"))
            .andExpect(jsonPath("$.subject").value("Sujet"))
            .andExpect(jsonPath("$.textContent").value("Corps"))
            .andRespond(withStatus(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"messageId\":\"<msg@brevo>\"}"));

        assertThatCode(() -> sender.send("student@vetspace.dz", "Sujet", "Corps"))
            .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void includesReplyToWhenGiven() {
        server.expect(requestTo(BrevoApiMailSender.DEFAULT_BASE_URL + "/v3/smtp/email"))
            .andExpect(jsonPath("$.replyTo.email").value("support@vetspace.dz"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        sender.send("student@vetspace.dz", "Sujet", "Corps", "support@vetspace.dz");
        server.verify();
    }

    @Test
    void aRejectedRequestBecomesAMailException() {
        // 401 = bad key. Callers must see the same exception family as an SMTP outage,
        // so registration still commits and reports verificationEmailSent=false.
        server.expect(requestTo(BrevoApiMailSender.DEFAULT_BASE_URL + "/v3/smtp/email"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"unauthorized\",\"message\":\"Key not found\"}"));

        assertThatThrownBy(() -> sender.send("student@vetspace.dz", "Sujet", "Corps"))
            .isInstanceOf(MailSendException.class)
            // The key travels only in the header; it must never leak into the exception
            // text that ends up in an ERROR log.
            .satisfies(e -> assertThat(String.valueOf(e)).doesNotContain(API_KEY));
    }
}
