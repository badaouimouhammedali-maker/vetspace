package com.vetspace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vetspace.BackendApplication;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Boots a real application context under @ActiveProfiles("prod") — via SpringApplicationBuilder
 * so the failure case can be observed rather than aborting the whole test class.
 *
 * <p>Unit-testing the rules is not enough here: the thing that must hold is that a bad
 * configuration actually stops the process. If the validator were mis-wired (wrong profile,
 * not a bean, exception swallowed), the unit tests would still pass while production booted
 * happily on a published signing key.
 */
@Testcontainers
class ProductionConfigValidatorContextTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    private static final String GOOD_SECRET = "n2Yb9Qw7ZK4pR8sT1vX3cF6hJ0mL5dA2gE7uI9oP4qS=";

    /**
     * Boots the app on the prod profile with sound config, overridden by {@code overrides}.
     *
     * <p>Values go in as command-line arguments, not {@code .properties()}: the latter lands
     * in Spring's {@code defaultProperties} source, which sits BELOW application.yml, so the
     * yaml defaults would win and this test would silently validate the wrong configuration.
     */
    private ConfigurableApplicationContext boot(String... overrides) {
        // Keyed, so an override REPLACES the default. Passing the same key twice on the
        // command line merges the values into a comma-joined list instead of overriding —
        // which silently produced "smtp,log" and a config that matched no mail sender at all.
        Map<String, String> args = new LinkedHashMap<>();
        args.put("spring.datasource.url", postgres.getJdbcUrl());
        args.put("spring.datasource.username", postgres.getUsername());
        args.put("spring.datasource.password", postgres.getPassword());
        args.put("spring.flyway.enabled", "true");
        args.put("server.port", "0");
        args.put("app.jwt.secret", GOOD_SECRET);
        args.put("app.mail.mode", "smtp");
        // The prod profile turns SMTP auth on, and the validator demands a real host and
        // credentials to go with it.
        args.put("spring.mail.host", "smtp.resend.com");
        args.put("spring.mail.username", "resend");
        args.put("app.cors.allowed-origins", "https://vetspace.vercel.app");
        for (String override : overrides) {
            int eq = override.indexOf('=');
            args.put(override.substring(0, eq), override.substring(eq + 1));
        }
        String[] argv = args.entrySet().stream()
            .map(e -> "--" + e.getKey() + "=" + e.getValue())
            .toArray(String[]::new);
        return new SpringApplicationBuilder(BackendApplication.class)
            .profiles("prod")
            .web(WebApplicationType.SERVLET)
            .run(argv);
    }

    @Test
    void startsOnTheProdProfileWithSoundConfiguration() {
        try (ConfigurableApplicationContext ctx = boot()) {
            assertThat(ctx.isRunning()).isTrue();
            // The validator is wired on this profile — not silently absent.
            assertThat(ctx.getBeansOfType(ProductionConfigValidator.class)).isNotEmpty();
        }
    }

    @Test
    void refusesToStartWithoutAJwtSecret() {
        assertThatThrownBy(() -> boot("app.jwt.secret="))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .rootCause()
            .hasMessageContaining("JWT_SECRET is not set");
    }

    @Test
    void refusesToStartOnTheKnownDefaultSecret() {
        assertThatThrownBy(() -> boot("app.jwt.secret=change-me"))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .rootCause()
            .hasMessageContaining("known default");
    }

    @Test
    void refusesToStartWithMailModeLog() {
        assertThatThrownBy(() -> boot("app.mail.mode=log"))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .rootCause()
            .hasMessageContaining("MAIL_MODE=log");
    }

    @Test
    void refusesToStartWithSmtpAuthButNoCredentials() {
        // Proves application-prod.yml's auth=true default actually reaches the validator:
        // wiping the username alone must abort the boot.
        assertThatThrownBy(() -> boot("spring.mail.username="))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .rootCause()
            .hasMessageContaining("MAIL_USERNAME is empty");
    }

    @Test
    void refusesToStartWithLocalhostCors() {
        assertThatThrownBy(() -> boot("app.cors.allowed-origins=http://localhost:3000"))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .rootCause()
            .hasMessageContaining("localhost");
    }
}
