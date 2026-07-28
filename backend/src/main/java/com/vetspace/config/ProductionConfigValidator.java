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
    private final String frontendUrl;
    private final String mailMode;
    private final String mailHost;
    private final String mailUsername;
    private final boolean mailSmtpAuth;
    private final String brevoApiKey;
    private final String corsAllowedOrigins;
    private final String adminPassword;
    private final boolean rateLimitsEnabled;
    private final boolean autoVerifyEmails;

    public ProductionConfigValidator(@Value("${app.jwt.secret:}") String jwtSecret,
                                     @Value("${app.frontend-url:}") String frontendUrl,
                                     @Value("${app.mail.mode:}") String mailMode,
                                     @Value("${spring.mail.host:}") String mailHost,
                                     @Value("${spring.mail.username:}") String mailUsername,
                                     @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean mailSmtpAuth,
                                     @Value("${app.mail.brevo-api-key:}") String brevoApiKey,
                                     @Value("${app.cors.allowed-origins:}") String corsAllowedOrigins,
                                     @Value("${app.seed.admin-password:}") String adminPassword,
                                     @Value("${app.rate-limits-enabled:true}") boolean rateLimitsEnabled,
                                     @Value("${app.auth.auto-verify-emails:false}") boolean autoVerifyEmails) {
        this.jwtSecret = jwtSecret;
        this.frontendUrl = frontendUrl;
        this.mailMode = mailMode;
        this.mailHost = mailHost;
        this.mailUsername = mailUsername;
        this.mailSmtpAuth = mailSmtpAuth;
        this.brevoApiKey = brevoApiKey;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.adminPassword = adminPassword;
        this.rateLimitsEnabled = rateLimitsEnabled;
        this.autoVerifyEmails = autoVerifyEmails;
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

        // --- Frontend URL ---------------------------------------------------
        // Every verification and password-reset email embeds this as the link base.
        // Unset falls back to http://localhost:3000, so the mail is delivered
        // successfully — and every link in it is dead on arrival.
        String frontend = frontendUrl == null ? "" : frontendUrl.trim();
        if (frontend.isEmpty() || frontend.toLowerCase(Locale.ROOT).contains("localhost")
            || frontend.contains("127.0.0.1")) {
            problems.add("FRONTEND_URL is unset or points at localhost, so every link in a "
                + "verification or password-reset email leads nowhere. Set it to the deployed "
                + "SPA origin, e.g. https://vetspace.vercel.app");
        } else if (!frontend.startsWith("https://")) {
            problems.add("FRONTEND_URL must be https:// in production — these links carry "
                + "one-time tokens.");
        }

        // --- Mail -----------------------------------------------------------
        String mode = String.valueOf(mailMode).trim();
        if ("log".equalsIgnoreCase(mode)) {
            problems.add("MAIL_MODE=log in production: password-reset and verification emails would "
                + "be printed to the log instead of sent, with no visible error. Set MAIL_MODE=smtp "
                + "or MAIL_MODE=brevo-api.");
        }
        // The SMTP coherence rules apply only when SMTP is the transport — with
        // MAIL_MODE=brevo-api the host and credentials are legitimately absent.
        if ("smtp".equalsIgnoreCase(mode)) {
            // MAIL_HOST unset falls back to application.yml's localhost — inside a container
            // that is the container itself, so every send fails (or hangs) with no hint that
            // the variable was simply never configured.
            String host = mailHost == null ? "" : mailHost.trim();
            if (host.isEmpty() || host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")) {
                problems.add("MAIL_MODE=smtp but MAIL_HOST is unset or points at localhost — inside "
                    + "the container there is no SMTP server there. Set MAIL_HOST to the provider's "
                    + "server, e.g. smtp.resend.com.");
            }
            // The trap this catches: the base yml defaults suit local mailpit (no auth), so
            // credentials configured with auth accidentally forced off are silently never
            // sent, and the provider rejects or stalls the session. Real registrations then
            // fail (or, before timeouts existed, hung) with no hint at the cause.
            String username = mailUsername == null ? "" : mailUsername.trim();
            if (!username.isEmpty() && !mailSmtpAuth) {
                problems.add("MAIL_USERNAME is set but MAIL_SMTP_AUTH is not true, so the credentials "
                    + "are never sent to the SMTP server. Set MAIL_SMTP_AUTH=true (and almost "
                    + "certainly MAIL_STARTTLS=true — every mainstream provider requires it on "
                    + "port 587).");
            }
            if (mailSmtpAuth && username.isEmpty()) {
                problems.add("MAIL_SMTP_AUTH=true but MAIL_USERNAME is empty. Set the provider's "
                    + "SMTP username and password.");
            }
        }
        if ("brevo-api".equalsIgnoreCase(mode) && (brevoApiKey == null || brevoApiKey.isBlank())) {
            problems.add("MAIL_MODE=brevo-api but BREVO_API_KEY is empty. Create one in the "
                + "Brevo dashboard (SMTP & API → API Keys) and set it.");
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

        // --- Test-only switches that must never be on in production -----------
        if (!rateLimitsEnabled) {
            problems.add("RATE_LIMITS_ENABLED=false disables every rate limiter — brute-force "
                + "protection on login, code redemption, signals and support. It exists for local "
                + "test runs only. Unset it.");
        }
        if (autoVerifyEmails) {
            problems.add("AUTO_VERIFY_EMAILS=true marks every new account verified without sending "
                + "mail, defeating email verification entirely. It exists for dev and e2e only. "
                + "Unset it.");
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
