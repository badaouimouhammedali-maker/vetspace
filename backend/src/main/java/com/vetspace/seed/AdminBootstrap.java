package com.vetspace.seed;

import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-shot first-admin bootstrap for a fresh production database, enabled with
 * SEED_ADMIN=true and meant to be turned off again on the next deploy.
 *
 * <p>Unlike {@link DevDataSeeder} this creates <em>only</em> the admin account — no sample
 * school, questions or activation codes ever land in a real database — and it is gated on a
 * property rather than the dev profile so it can run in prod. It is idempotent: once any
 * ADMIN exists it does nothing, so leaving the flag on by accident cannot mint a second
 * admin or reset the first one's password.
 */
@Component
@ConditionalOnProperty(name = "app.seed.admin-enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    /** A first admin on a public deployment is a high-value credential; refuse an obviously weak one. */
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminUsername;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          @Value("${app.seed.admin-email:}") String adminEmail,
                          @Value("${app.seed.admin-password:}") String adminPassword,
                          @Value("${app.seed.admin-username:admin}") String adminUsername) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminUsername = adminUsername;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.countByRole(Role.ADMIN) > 0) {
            log.info("SEED_ADMIN is on but an admin already exists — nothing to do. Remove SEED_ADMIN.");
            return;
        }
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.error("SEED_ADMIN is on but ADMIN_EMAIL/ADMIN_PASSWORD are not set — no admin created.");
            return;
        }
        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            log.error("SEED_ADMIN: ADMIN_PASSWORD is shorter than {} characters — no admin created.",
                MIN_PASSWORD_LENGTH);
            return;
        }
        if (userRepository.findByEmailIgnoreCase(adminEmail).isPresent()) {
            log.error("SEED_ADMIN: a user already exists with that email — no admin created.");
            return;
        }
        if (userRepository.findByUsername(adminUsername).isPresent()) {
            log.error("SEED_ADMIN: username '{}' is taken — set ADMIN_USERNAME to a free one.", adminUsername);
            return;
        }

        userRepository.save(User.builder()
            .email(adminEmail)
            .username(adminUsername)
            .passwordHash(passwordEncoder.encode(adminPassword))
            .lastName("Admin")
            .firstName("VetSpace")
            .role(Role.ADMIN)
            .status(UserStatus.ACTIVE)
            // Created by an operator from env vars, not by self-registration: there is no
            // link to click, and requiring one would lock the first admin out of the app.
            .emailVerified(true)
            .build());

        // Email only — the password is never logged.
        log.info("SEED_ADMIN: created the first admin ({}). Remove SEED_ADMIN and rotate this "
            + "password from the app now.", adminEmail);
    }
}
