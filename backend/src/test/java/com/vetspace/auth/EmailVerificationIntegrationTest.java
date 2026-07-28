package com.vetspace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vetspace.auth.dto.RegisterRequest;
import com.vetspace.auth.dto.RegisterResponse;
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
import org.springframework.mail.MailSendException;
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
        /** Flip on to simulate an SMTP outage — the exact shape JavaMailSender throws. */
        volatile boolean failing = false;

        @Override
        public void send(String to, String subject, String body) {
            if (failing) {
                throw new MailSendException("simulated SMTP outage");
            }
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
        // The sender is a singleton bean shared by every test in the class, so an
        // outage simulated by one test would otherwise leak into the next.
        mailSender.failing = false;
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
        verificationService.trySendVerification(user);
        assertThat(mailSender.recipients).containsExactly(user.getEmail());

        verificationService.verify(lastTokenFromEmail());

        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
        // Login now succeeds.
        assertThat(authService.login(user.getEmail(), PASSWORD).accessToken()).isNotBlank();
    }

    @Test
    void onlyTheHashIsStoredNeverTheRawToken() {
        verificationService.trySendVerification(user);
        String rawToken = lastTokenFromEmail();

        List<EmailVerificationToken> stored = tokenRepository.findAll();
        assertThat(stored).isNotEmpty();
        assertThat(stored).noneMatch(t -> t.getTokenHash().equals(rawToken));
        // Only the hash of the raw value resolves it — a DB dump cannot be replayed.
        assertThat(tokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken))).isPresent();
    }

    @Test
    void aTokenIsSingleUse() {
        verificationService.trySendVerification(user);
        String rawToken = lastTokenFromEmail();
        verificationService.verify(rawToken);

        assertThatThrownBy(() -> verificationService.verify(rawToken))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("invalide ou expiré");
    }

    @Test
    void anExpiredTokenIsRejected() {
        verificationService.trySendVerification(user);
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
        verificationService.trySendVerification(user);
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

    // ---------------------------------------------------------------
    // A mail outage must not cost anybody their account
    // ---------------------------------------------------------------

    /**
     * The bug: {@code register} was {@code @Transactional} with the send inside it, so
     * any SMTP failure rolled the registration back and answered 500. The student was
     * left with no account, no email, and nothing to retry — for an outage that was not
     * theirs. Registration must outlive the mail server.
     */
    @Test
    void registrationSurvivesAMailOutageAndSaysSoInTheResponse() {
        School school = user.getSchool();
        String email = "outage-" + UUID.randomUUID() + "@vetspace.dz";
        mailSender.failing = true;

        RegisterResponse response = authService.register(new RegisterRequest(
            "Nom", "Prénom", "out" + UUID.randomUUID().toString().substring(0, 8),
            email, "correct-horse-battery", school.getId(), 3, null));

        // 1. The account exists and is durable — the whole point.
        assertThat(response.id()).isNotNull();
        User persisted = userRepository.findByEmailIgnoreCase(email).orElse(null);
        assertThat(persisted).as("account committed despite the mail failure").isNotNull();
        assertThat(persisted.isEmailVerified()).isFalse();
        // And the response says so, which is what routes the UI to the verify screen
        // instead of a login that would refuse the account.
        assertThat(response.emailVerified()).isFalse();

        // 2. The response admits the mail did not go out, so the UI can offer "renvoyer"
        //    rather than pointing the student at an inbox that will stay empty.
        assertThat(response.verificationEmailSent()).isFalse();

        // 3. Nothing was actually delivered.
        assertThat(mailSender.bodies).isEmpty();
    }

    /** And once the mail server is back, the ordinary resend path still works. */
    @Test
    void resendWorksAfterARegistrationWhoseMailFailed() {
        School school = user.getSchool();
        String email = "recover-" + UUID.randomUUID() + "@vetspace.dz";
        mailSender.failing = true;
        RegisterResponse response = authService.register(new RegisterRequest(
            "Nom", "Prénom", "rec" + UUID.randomUUID().toString().substring(0, 8),
            email, "correct-horse-battery", school.getId(), 3, null));
        assertThat(response.verificationEmailSent()).isFalse();

        mailSender.failing = false;
        verificationService.resend(email);

        assertThat(mailSender.recipients).containsExactly(email);
        // The link from the resend genuinely verifies the account created during the outage.
        verificationService.verify(lastTokenFromEmail());
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().isEmailVerified()).isTrue();
    }

    /** A healthy send reports success, so the flag is not simply hard-coded false. */
    @Test
    void registrationReportsSuccessWhenTheMailGoesOut() {
        School school = user.getSchool();
        String email = "happy-" + UUID.randomUUID() + "@vetspace.dz";

        RegisterResponse response = authService.register(new RegisterRequest(
            "Nom", "Prénom", "hap" + UUID.randomUUID().toString().substring(0, 8),
            email, "correct-horse-battery", school.getId(), 3, null));

        assertThat(response.verificationEmailSent()).isTrue();
        assertThat(mailSender.recipients).containsExactly(email);
    }

    /** Resend must not 500 either — same reasoning, different entry point. */
    @Test
    void resendDoesNotThrowWhenTheMailServerIsDown() {
        mailSender.failing = true;
        verificationService.resend(user.getEmail());

        // The token still rotated, so a later attempt has something to deliver.
        assertThat(tokenRepository.findAll()).isNotEmpty();
    }
}
