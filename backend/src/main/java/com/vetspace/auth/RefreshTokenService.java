package com.vetspace.auth;

import com.vetspace.domain.user.RefreshToken;
import com.vetspace.domain.user.User;
import com.vetspace.repository.RefreshTokenRepository;
import com.vetspace.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Issues, rotates and revokes refresh tokens. Only the SHA-256 hash is ever persisted; the raw value lives solely in the httpOnly cookie. */
@Service
public class RefreshTokenService {

    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final int TOKEN_BYTE_LENGTH = 32; // 256 bits
    public static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/auth";

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService self;
    private final String sameSite;
    private final boolean secure;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, @Lazy RefreshTokenService self,
                               @Value("${app.cookie.same-site:Strict}") String sameSite,
                               @Value("${app.cookie.secure:true}") boolean secure) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.sameSite = sameSite;
        this.secure = secure;
        // Self-injected proxy: revokeFamily() must run in its own REQUIRES_NEW transaction so the
        // revocation survives even though the caller (AuthService.refresh) goes on to throw and roll
        // back. Calling "this.revokeFamily(...)" directly would bypass the proxy and silently ignore
        // that propagation setting, since Spring's transactional advice only applies to calls that go
        // through the bean's proxy.
        this.self = self;
    }

    @Transactional
    public IssuedRefreshToken issueNewFamily(User user) {
        return issue(user, UUID.randomUUID());
    }

    private IssuedRefreshToken issue(User user, UUID familyId) {
        String rawToken = TokenHasher.randomUrlSafeToken(TOKEN_BYTE_LENGTH);
        RefreshToken token = RefreshToken.builder()
            .user(user)
            .tokenHash(TokenHasher.sha256Hex(rawToken))
            .familyId(familyId)
            .expiresAt(Instant.now().plus(REFRESH_TOKEN_TTL))
            .build();
        refreshTokenRepository.save(token);
        return new IssuedRefreshToken(rawToken, token);
    }

    /** Validates a presented raw refresh token, rotating it. Reuse of an already-rotated/revoked token revokes the whole family. */
    @Transactional
    public RotationResult rotate(String rawToken) {
        Optional<RefreshToken> maybeToken = refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken));
        if (maybeToken.isEmpty()) {
            return new RotationResult.Invalid();
        }
        RefreshToken token = maybeToken.get();
        if (token.getRevokedAt() != null) {
            self.revokeFamily(token.getFamilyId());
            return new RotationResult.ReuseDetected();
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            return new RotationResult.Invalid();
        }
        token.setRevokedAt(Instant.now());
        refreshTokenRepository.save(token);
        return new RotationResult.Rotated(issue(token.getUser(), token.getFamilyId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(UUID familyId) {
        List<RefreshToken> active = refreshTokenRepository.findByFamilyIdAndRevokedAtIsNull(familyId);
        Instant now = Instant.now();
        active.forEach(t -> t.setRevokedAt(now));
        refreshTokenRepository.saveAll(active);
    }

    @Transactional
    public void revokeToken(String rawToken) {
        refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken)).ifPresent(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(Instant.now());
                refreshTokenRepository.save(t);
            }
        });
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        List<RefreshToken> active = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        Instant now = Instant.now();
        active.forEach(t -> t.setRevokedAt(now));
        refreshTokenRepository.saveAll(active);
    }

    /**
     * SameSite defaults to Strict, which is right when the SPA is served from the same
     * origin as the API (local dev, the nginx-proxied container stack). A split deploy —
     * SPA on Vercel, API on Railway — makes every API call cross-site, and a Strict (or
     * Lax) cookie is simply not attached to those requests, so refresh silently fails and
     * users are logged out after the 15-minute access token expires. Prod therefore sets
     * SameSite=None, which browsers only honour together with Secure.
     */
    public ResponseCookie buildCookie(String rawValue) {
        return baseCookie(rawValue).maxAge(REFRESH_TOKEN_TTL).build();
    }

    public ResponseCookie expiredCookie() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path(COOKIE_PATH);
    }

    public record IssuedRefreshToken(String rawValue, RefreshToken entity) {
    }

    public sealed interface RotationResult {
        record Rotated(IssuedRefreshToken issued) implements RotationResult {
        }

        record ReuseDetected() implements RotationResult {
        }

        record Invalid() implements RotationResult {
        }
    }
}
