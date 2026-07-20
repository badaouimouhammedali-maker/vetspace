package com.vetspace.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The SEED_ADMIN bootstrap is the only way an admin exists on a fresh production
 * database, and it runs with real credentials in a real deployment — so its guard rails
 * (idempotency, weak-password refusal) are worth pinning down.
 */
@SpringBootTest
@Testcontainers
class AdminBootstrapIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("app.recaptcha.enabled", () -> false);
        // Registration now sends a verification email and login requires a verified
        // address; these suites predate that and are not testing it.
        registry.add("app.auth.auto-verify-emails", () -> true);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * The container is shared across methods with no rollback, so an admin left behind by
     * one test would silently turn the next one into a no-op. Start every test from
     * "no admin exists" rather than depending on execution order.
     */
    @BeforeEach
    void removeExistingAdmins() {
        userRepository.deleteAll(userRepository.findAll().stream()
            .filter(u -> u.getRole() == Role.ADMIN)
            .toList());
    }

    private AdminBootstrap bootstrap(String email, String password, String username) {
        return new AdminBootstrap(userRepository, passwordEncoder, email, password, username);
    }

    private String unique() {
        return Long.toString(System.nanoTime(), 36);
    }

    @Test
    void createsTheFirstAdminWithAHashedPassword() {
        String suffix = unique();
        String email = "boot-" + suffix + "@vetspace.dz";
        String rawPassword = "a-long-enough-password";

        bootstrap(email, rawPassword, "admin-" + suffix).run((ApplicationArguments) null);

        User created = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.ADMIN);
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
        // The password is stored only as a BCrypt hash, never in the clear.
        assertThat(created.getPasswordHash()).isNotEqualTo(rawPassword).startsWith("$2");
        assertThat(passwordEncoder.matches(rawPassword, created.getPasswordHash())).isTrue();
    }

    @Test
    void isANoOpOnceAnAdminExists() {
        String suffix = unique();
        bootstrap("first-" + suffix + "@vetspace.dz", "a-long-enough-password", "admin-" + suffix)
            .run((ApplicationArguments) null);
        long adminsAfterFirst = userRepository.countByRole(Role.ADMIN);

        // Leaving SEED_ADMIN on by mistake must not mint a second admin.
        String second = "second-" + suffix + "@vetspace.dz";
        bootstrap(second, "another-long-password", "admin2-" + suffix).run((ApplicationArguments) null);

        assertThat(userRepository.countByRole(Role.ADMIN)).isEqualTo(adminsAfterFirst);
        assertThat(userRepository.findByEmailIgnoreCase(second)).isEmpty();
    }

    @Test
    void refusesAShortPassword() {
        String suffix = unique();
        String email = "weak-" + suffix + "@vetspace.dz";

        bootstrap(email, "short", "admin-" + suffix).run((ApplicationArguments) null);

        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void refusesBlankCredentials() {
        bootstrap("", "", "admin-" + unique()).run((ApplicationArguments) null);
        // Nothing to assert beyond "did not blow up and created nothing" — a blank
        // config must never produce a passwordless admin.
        assertThat(userRepository.findByEmailIgnoreCase("")).isEmpty();
    }
}
