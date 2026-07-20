package com.vetspace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vetspace.domain.school.School;
import com.vetspace.domain.user.EmailVerificationToken;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.extras.DailyUserRateLimiter;
import com.vetspace.mail.AppMailSender;
import com.vetspace.repository.EmailVerificationTokenRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.TokenHasher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verification with the flag OFF — i.e. how production behaves. The other suites set
 * auto-verify on; this one is the reason that flag has to stay off by default.
 */
@SpringBootTest
@Testcontainers
class EmailVerificationIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("app.recaptcha.enabled", () -> false);
        // The whole point of this suite: production behaviour.
        registry.add("app.auth.auto-verify-emails", () -> false);
    }

    /** Captures outbound mail so the verification link can be followed. */
    static class RecordingMailSender implements AppMailSender {
        final List<String> bodies = new CopyOnWriteArrayList<>();
        final List<String> recipients = new CopyOnWriteArrayList<>();

        @Override
        public void send(String to, String subject, String body) {
            recipients.add(to);
            bodies.add(body);
        }
    }

    @TestConfiguration
    static class MailConfig {
        @Bean
        @Primary
        RecordingMailSender recordingMailSender() {
            return new RecordingMailSender();
        }
    }

    private static final String PASSWORD = "correct-horse-battery";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @Autowired
    private EmailVerificationService verificationService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RecordingMailSender mailSender;

    @Autowired
    private DailyUserRateLimiter rateLimiter;

    private User user;

    @BeforeEach
    void setUp() {
        mailSender.bodies.clear();
        mailSender.recipients.clear();
        rateLimiter.resetForTests();

        School school = schoolRepository.save(School.builder()
            .name("ENSV Alger").slug("ensv-" + UUID.randomUUID()).build());
        user = userRepository.save(User.builder()
            .email("verify-" + UUID.randomUUID() + "@vetspace.dz")
            .username("ver-" + UUID.randomUUID().toString().substring(0, 8))
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .lastName("Test").firstName("Verify")
            .role(Role.STUDENT).status(UserStatus.ACTIVE)
            .emailVerified(false)
            .school(school).studyYear(3)
            .build());
    }

    private String lastTokenFromEmail() {
        Matcher m = TOKEN_IN_LINK.matcher(mailSender.bodies.get(mailSender.bodies.size() - 1));
        assertThat(m.find()).as("verification link in the email body").isTrue();
        return m.group(1);
    }

    @Test
    void loginIsRefusedUntilTheAddressIsVerified() {
        assertThatThrownBy(() -> authService.login(user.getEmail(), PASSWORD))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                ResponseStatusException ex = (ResponseStatusException) e;
                assertThat(ex.getStatusCode().value()).isEqualTo(403);
                // A machine-readable marker so the SPA can route to the resend screen.
                assertThat(ex.getReason()).isEqualTo(AuthService.EMAIL_NOT_VERIFIED);
            });
    }

    @Test
    void theEmailedTokenVerifiesTheAccountAndUnlocksLogin() {
        verificationService.sendVerification(user);
        assertThat(mailSender.recipients).containsExactly(user.getEmail());

        verificationService.verify(lastTokenFromEmail());

        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
        // Login now succeeds.
        assertThat(authService.login(user.getEmail(), PASSWORD).accessToken()).isNotBlank();
    }

    @Test
    void onlyTheHashIsStoredNeverTheRawToken() {
        verificationService.sendVerification(user);
        String rawToken = lastTokenFromEmail();

        List<EmailVerificationToken> stored = tokenRepository.findAll();
        assertThat(stored).isNotEmpty();
        assertThat(stored).noneMatch(t -> t.getTokenHash().equals(rawToken));
        // Only the hash of the raw value resolves it — a DB dump cannot be replayed.
        assertThat(tokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken))).isPresent();
    }

    @Test
    void aTokenIsSingleUse() {
        verificationService.sendVerification(user);
        String rawToken = lastTokenFromEmail();
        verificationService.verify(rawToken);

        assertThatThrownBy(() -> verificationService.verify(rawToken))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("invalide ou expiré");
    }

    @Test
    void anExpiredTokenIsRejected() {
        verificationService.sendVerification(user);
        String rawToken = lastTokenFromEmail();

        EmailVerificationToken token =
            tokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken)).orElseThrow();
        token.setExpiresAt(Instant.now().minusSeconds(1));
        tokenRepository.save(token);

        assertThatThrownBy(() -> verificationService.verify(rawToken))
            .isInstanceOf(ResponseStatusException.class);
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void anUnknownTokenIsRejected() {
        assertThatThrownBy(() -> verificationService.verify("not-a-real-token"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void resendIsCappedAtThreePerHour() {
        for (int i = 0; i < EmailVerificationService.RESEND_LIMIT_PER_HOUR; i++) {
            verificationService.resend(user.getEmail());
        }
        assertThat(mailSender.bodies).hasSize(EmailVerificationService.RESEND_LIMIT_PER_HOUR);

        assertThatThrownBy(() -> verificationService.resend(user.getEmail()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(429));
    }

    @Test
    void resendInvalidatesThePreviousLink() {
        verificationService.sendVerification(user);
        String firstToken = lastTokenFromEmail();

        verificationService.resend(user.getEmail());

        // The superseded link must not still work.
        assertThatThrownBy(() -> verificationService.verify(firstToken))
            .isInstanceOf(ResponseStatusException.class);
        verificationService.verify(lastTokenFromEmail());
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void resendRevealsNothingAboutUnknownOrAlreadyVerifiedAddresses() {
        // Neither case throws, and neither sends mail — so the response cannot be used
        // to discover which addresses are registered.
        verificationService.resend("nobody-" + UUID.randomUUID() + "@vetspace.dz");

        user.setEmailVerified(true);
        userRepository.save(user);
        verificationService.resend(user.getEmail());

        assertThat(mailSender.bodies).isEmpty();
    }
}
