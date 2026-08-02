package com.vetspace.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

/**
 * The refresh cookie's attributes are the difference between a working split deploy and
 * users being silently logged out every 15 minutes, so pin them down directly.
 *
 * <p>Pure unit test: cookie construction depends on no collaborator except configuration.
 */
class RefreshCookieAttributesTest {

    private RefreshTokenService serviceWith(String sameSite, boolean secure) {
        // The concurrent-session limit plays no part in cookie construction; 1 is the default.
        return new RefreshTokenService(null, null, sameSite, secure, 1);
    }

    @Test
    void defaultsToStrictAndSecureForSameOriginDeployments() {
        ResponseCookie cookie = serviceWith("Strict", true).buildCookie("raw-token");

        assertThat(cookie.getName()).isEqualTo(RefreshTokenService.COOKIE_NAME);
        assertThat(cookie.getValue()).isEqualTo("raw-token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(RefreshTokenService.REFRESH_TOKEN_TTL);
    }

    @Test
    void carriesSameSiteNoneWithSecureForTheCrossDomainSpa() {
        ResponseCookie cookie = serviceWith("None", true).buildCookie("raw-token");

        assertThat(cookie.getSameSite()).isEqualTo("None");
        // Browsers reject SameSite=None unless Secure is also set — the two travel together.
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    void logoutCookieClearsTheValueAndKeepsTheSameAttributes() {
        ResponseCookie cookie = serviceWith("None", true).expiredCookie();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge().isZero()).isTrue();
        // Attributes must match the cookie being cleared, or the browser keeps the original.
        assertThat(cookie.getSameSite()).isEqualTo("None");
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
    }
}
