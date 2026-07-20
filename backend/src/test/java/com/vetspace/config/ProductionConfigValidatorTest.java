package com.vetspace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Each case here corresponds to a production incident the validator exists to prevent.
 * The unit tests below pin the rules; {@link ProductionConfigValidatorContextTest} proves
 * the same rules actually abort a prod-profile application context.
 */
class ProductionConfigValidatorTest {

    private static final String GOOD_SECRET = "n2Yb9Qw7ZK4pR8sT1vX3cF6hJ0mL5dA2gE7uI9oP4qS=";
    private static final String GOOD_ORIGINS = "https://vetspace.vercel.app";

    private ProductionConfigValidator validator(String secret, String mailMode, String origins,
                                                String adminPassword) {
        return validator(secret, mailMode, origins, adminPassword, true, false);
    }

    private ProductionConfigValidator validator(String secret, String mailMode, String origins,
                                                String adminPassword, boolean rateLimitsEnabled,
                                                boolean autoVerifyEmails) {
        return new ProductionConfigValidator(secret, mailMode, origins, adminPassword,
            rateLimitsEnabled, autoVerifyEmails);
    }

    private ProductionConfigValidator valid() {
        return validator(GOOD_SECRET, "smtp", GOOD_ORIGINS, "");
    }

    @Test
    void acceptsASoundProductionConfiguration() {
        assertThatCode(() -> valid().validate()).doesNotThrowAnyException();
    }

    @Test
    void rejectsAMissingJwtSecret() {
        assertThatThrownBy(() -> validator("", "smtp", GOOD_ORIGINS, "").validate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET is not set");
    }

    @Test
    void rejectsTheKnownDefaultSecret() {
        // The exact value that used to be application.yml's fallback.
        assertThatThrownBy(() -> validator("change-me", "smtp", GOOD_ORIGINS, "").validate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("known default");
    }

    @Test
    void rejectsSecretsUnder32Bytes() {
        assertThatThrownBy(() -> validator("a".repeat(31), "smtp", GOOD_ORIGINS, "").validate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("shorter than 32 bytes");

        assertThatCode(() -> validator("a".repeat(32), "smtp", GOOD_ORIGINS, "").validate())
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsMailModeLog() {
        assertThatThrownBy(() -> validator(GOOD_SECRET, "log", GOOD_ORIGINS, "").validate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("MAIL_MODE=log");
    }

    @Test
    void rejectsEmptyCorsOrigins() {
        assertThatThrownBy(() -> validator(GOOD_SECRET, "smtp", "  ", "").validate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CORS_ALLOWED_ORIGINS is empty");
    }

    @Test
    void rejectsLocalhostCorsOrigins() {
        assertThatThrownBy(() -> validator(GOOD_SECRET, "smtp", "http://localhost:3000", "").validate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("localhost");

        // Also when a real origin is present alongside a stray local one.
        assertThatThrownBy(() ->
            validator(GOOD_SECRET, "smtp", GOOD_ORIGINS + ",http://127.0.0.1:3000", "").validate())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAShortAdminPasswordButAllowsItUnset() {
        assertThatThrownBy(() -> validator(GOOD_SECRET, "smtp", GOOD_ORIGINS, "short").validate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ADMIN_PASSWORD");

        // Unset is the normal steady state once the admin exists.
        assertThatCode(() -> validator(GOOD_SECRET, "smtp", GOOD_ORIGINS, "").validate())
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsDisabledRateLimits() {
        // The switch exists so repeated local test runs do not trip the redeem and login
        // limits. In production it would remove brute-force protection outright.
        assertThatThrownBy(() ->
            validator(GOOD_SECRET, "smtp", GOOD_ORIGINS, "", false, false).validate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("RATE_LIMITS_ENABLED=false");
    }

    @Test
    void rejectsAutoVerifiedEmails() {
        assertThatThrownBy(() ->
            validator(GOOD_SECRET, "smtp", GOOD_ORIGINS, "", true, true).validate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AUTO_VERIFY_EMAILS=true");
    }

    @Test
    void reportsEveryProblemAtOnceAndLeaksNoValues() {
        // Distinctive values so "was this echoed back?" is unambiguous — note the
        // message legitimately contains the word "shorter", so the probe values must
        // not be substrings of the explanatory text.
        String secretValue = "zzTOPSECRETzz";
        String passwordValue = "qqPWqq"; // under 12 chars, so the ADMIN_PASSWORD rule fires

        assertThatThrownBy(() ->
            validator(secretValue, "log", "http://localhost:3000", passwordValue).validate())
            .isInstanceOf(IllegalStateException.class)
            .satisfies(e -> {
                String msg = e.getMessage();
                // All four surface together, so one redeploy fixes everything.
                assertThat(msg).contains("JWT_SECRET").contains("MAIL_MODE")
                    .contains("CORS_ALLOWED_ORIGINS").contains("ADMIN_PASSWORD");
                // Credentials must never be echoed into a log or a crash trace.
                assertThat(msg).doesNotContain(secretValue).doesNotContain(passwordValue);
            });
    }
}
