package com.vetspace.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.vetspace.domain.school.School;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Lockout is a security control, so the tests care as much about what it does NOT reveal
 * as about what it blocks: the response to a locked account must be indistinguishable from
 * a wrong password, or it becomes an oracle for enumerating accounts and timing retries.
 */
@SpringBootTest
@Testcontainers
class AccountLockoutIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("app.recaptcha.enabled", () -> false);
    }

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setUp() {
        School school = schoolRepository.save(School.builder()
            .name("ENSV Alger").slug("ensv-" + UUID.randomUUID()).build());
        user = userRepository.save(User.builder()
            .email("lock-" + UUID.randomUUID() + "@vetspace.dz")
            .username("lock-" + UUID.randomUUID().toString().substring(0, 8))
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .lastName("Test").firstName("Lock")
            .role(Role.STUDENT).status(UserStatus.ACTIVE)
            .school(school).studyYear(3)
            .build());
    }

    /** Attempts a login and returns the failure message, or null when it succeeded. */
    private String attempt(String password) {
        try {
            authService.login(user.getEmail(), password);
            return null;
        } catch (ResponseStatusException e) {
            return e.getReason();
        }
    }

    private User reload() {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    @Test
    void locksTheAccountAfterFiveFailuresInTheWindow() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            assertThat(attempt("wrong-password")).isEqualTo(AuthService.INVALID_CREDENTIALS);
        }

        assertThat(reload().getLockedUntil()).isNotNull().isAfter(Instant.now());
        // The correct password is refused while locked — that is the whole point.
        assertThat(attempt(PASSWORD)).isEqualTo(AuthService.INVALID_CREDENTIALS);
    }

    @Test
    void doesNotLockBeforeTheThreshold() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS - 1; i++) {
            attempt("wrong-password");
        }

        assertThat(reload().getLockedUntil()).isNull();
        // Still usable with the right password.
        assertThat(attempt(PASSWORD)).isNull();
    }

    @Test
    void theLockExpiresAndTheAccountWorksAgain() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            attempt("wrong-password");
        }
        assertThat(attempt(PASSWORD)).isEqualTo(AuthService.INVALID_CREDENTIALS);

        // Simulate the lock lapsing rather than waiting 15 real minutes.
        User locked = reload();
        locked.setLockedUntil(Instant.now().minusSeconds(1));
        userRepository.save(locked);

        assertThat(attempt(PASSWORD)).isNull();
        // Expiry also cleared the counters, so the next failure starts a fresh window.
        User after = reload();
        assertThat(after.getLockedUntil()).isNull();
        assertThat(after.getFailedLoginAttempts()).isZero();
    }

    @Test
    void aSuccessfulLoginResetsTheCounter() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS - 1; i++) {
            attempt("wrong-password");
        }
        assertThat(reload().getFailedLoginAttempts()).isEqualTo(LoginAttemptService.MAX_ATTEMPTS - 1);

        assertThat(attempt(PASSWORD)).isNull();

        assertThat(reload().getFailedLoginAttempts()).isZero();
        assertThat(reload().getFirstFailedLoginAt()).isNull();
    }

    @Test
    void theLockedMessageIsIdenticalToWrongPasswordAndUnknownAccount() {
        String wrongPassword = attempt("wrong-password");

        for (int i = 1; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            attempt("wrong-password");
        }
        String locked = attempt(PASSWORD);

        String unknownAccount;
        try {
            authService.login("no-such-user-" + UUID.randomUUID() + "@vetspace.dz", "whatever");
            unknownAccount = null;
        } catch (ResponseStatusException e) {
            unknownAccount = e.getReason();
        }

        // Byte-identical across all three: no oracle for "does this account exist", for
        // "is it locked", or for "has the lock expired yet".
        assertThat(locked).isEqualTo(wrongPassword).isEqualTo(unknownAccount)
            .isEqualTo(AuthService.INVALID_CREDENTIALS);
    }

    @Test
    void failureCountSurvivesInTheDatabase() {
        attempt("wrong-password");
        attempt("wrong-password");

        // Persisted, not in-memory: a restart between attempts must not reset the counter.
        assertThat(reload().getFailedLoginAttempts()).isEqualTo(2);
        assertThat(reload().getFirstFailedLoginAt()).isNotNull();
    }
}
