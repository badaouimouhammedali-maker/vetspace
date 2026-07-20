package com.vetspace.config;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Refuses to start the prod profile on a configuration that would be quietly unsafe or
 * quietly broken at runtime.
 *
 * <p>Every check here exists because the failure it prevents is invisible: a missing
 * JWT_SECRET used to fall back to a published default (anyone could then mint an admin
 * token), MAIL_MODE=log makes password resets look like they work while the mail goes to
 * stdout, and a localhost CORS origin means the real SPA is silently refused. Failing loudly
 * at boot beats any of those.
 *
 * <p>Throwing from @PostConstruct aborts the Spring context, which exits non-zero — so a
 * platform deploy fails visibly rather than serving a broken app.
 */
@Component
@Profile("prod")
public class ProductionConfigValidator {

    /** Values that have appeared as defaults or examples; none may reach production. */
    private static final Set<String> KNOWN_DEFAULT_SECRETS = Set.of(
        "change-me", "changeme", "secret", "please-change", "your-secret-here",
        "e2e-only-secret-not-for-prod", "parity-only-secret-not-for-production-use");
    private static final int MIN_SECRET_BYTES = 32;
    private static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

    private final String jwtSecret;
    private final String mailMode;
    private final String corsAllowedOrigins;
    private final String adminPassword;

    public ProductionConfigValidator(@Value("${app.jwt.secret:}") String jwtSecret,
                                     @Value("${app.mail.mode:}") String mailMode,
                                     @Value("${app.cors.allowed-origins:}") String corsAllowedOrigins,
                                     @Value("${app.seed.admin-password:}") String adminPassword) {
        this.jwtSecret = jwtSecret;
        this.mailMode = mailMode;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.adminPassword = adminPassword;
    }

    @PostConstruct
    void validate() {
        List<String> problems = new ArrayList<>();

        // --- JWT signing key ------------------------------------------------
        String secret = jwtSecret == null ? "" : jwtSecret.trim();
        if (secret.isEmpty()) {
            problems.add("JWT_SECRET is not set. Generate one with: openssl rand -base64 48");
        } else if (KNOWN_DEFAULT_SECRETS.contains(secret.toLowerCase(Locale.ROOT))) {
            problems.add("JWT_SECRET is a known default value. Anyone could forge tokens, including "
                + "admin ones. Generate a real secret with: openssl rand -base64 48");
        } else if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            problems.add("JWT_SECRET is shorter than " + MIN_SECRET_BYTES + " bytes ("
                + secret.getBytes(StandardCharsets.UTF_8).length + "). Generate one with: "
                + "openssl rand -base64 48");
        }

        // --- Mail -----------------------------------------------------------
        if ("log".equalsIgnoreCase(String.valueOf(mailMode).trim())) {
            problems.add("MAIL_MODE=log in production: password-reset and verification emails would "
                + "be printed to the log instead of sent, with no visible error. Set MAIL_MODE=smtp.");
        }

        // --- CORS -----------------------------------------------------------
        String origins = corsAllowedOrigins == null ? "" : corsAllowedOrigins.trim();
        if (origins.isEmpty()) {
            problems.add("CORS_ALLOWED_ORIGINS is empty. Set it to the exact SPA origin, "
                + "e.g. https://vetspace.vercel.app");
        } else if (origins.toLowerCase(Locale.ROOT).contains("localhost")
            || origins.contains("127.0.0.1")) {
            problems.add("CORS_ALLOWED_ORIGINS contains a localhost origin (" + origins + "). "
                + "Set it to the deployed SPA origin only.");
        }

        // --- First-admin bootstrap -----------------------------------------
        if (adminPassword != null && !adminPassword.isEmpty()
            && adminPassword.length() < MIN_ADMIN_PASSWORD_LENGTH) {
            problems.add("ADMIN_PASSWORD is shorter than " + MIN_ADMIN_PASSWORD_LENGTH
                + " characters. Use a longer one, or unset it if the admin already exists.");
        }

        if (!problems.isEmpty()) {
            StringBuilder message = new StringBuilder(
                "\n\n*** Refusing to start: unsafe production configuration ***\n");
            for (int i = 0; i < problems.size(); i++) {
                message.append("  ").append(i + 1).append(". ").append(problems.get(i)).append('\n');
            }
            message.append("\nSee docs/deploy.md for the full variable list.\n");
            // No values are echoed back — only which variable is wrong and why.
            throw new IllegalStateException(message.toString());
        }
    }
}
