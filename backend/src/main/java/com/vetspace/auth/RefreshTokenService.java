package com.vetspace.auth;

import com.vetspace.domain.user.RefreshToken;
import com.vetspace.domain.user.RevocationReason;
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
    private final int maxConcurrentSessions;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, @Lazy RefreshTokenService self,
                               @Value("${app.cookie.same-site:Strict}") String sameSite,
                               @Value("${app.cookie.secure:true}") boolean secure,
                               @Value("${app.auth.max-concurrent-sessions:1}") int maxConcurrentSessions) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.sameSite = sameSite;
        this.secure = secure;
        // Clamped rather than validated-and-thrown: a typo in an env var should loosen or
        // tighten the policy, never stop the application from starting.
        this.maxConcurrentSessions = Math.max(1, maxConcurrentSessions);
        // Self-injected proxy: revokeFamily() must run in its own REQUIRES_NEW transaction so the
        // revocation survives even though the caller (AuthService.refresh) goes on to throw and roll
        // back. Calling "this.revokeFamily(...)" directly would bypass the proxy and silently ignore
        // that propagation setting, since Spring's transactional advice only applies to calls that go
        // through the bean's proxy.
        this.self = self;
    }

    /**
     * Issues the family for a fresh login, enforcing the concurrent-session policy first.
     *
     * <p>With {@code MAX_CONCURRENT_SESSIONS=1} (the default, and the anti-sharing policy)
     * every other live family is revoked: logging in here closes everywhere else. Above 1,
     * only the excess is evicted, least-recently-used first, so the device someone is
     * actively working on is the last to be taken away from them.
     *
     * <p>Order matters and is not interchangeable: revoke first, then issue. Issuing first
     * would put the new family in the candidate list and — at max=1 — immediately revoke
     * the token the user is being handed.
     */
    @Transactional
    public IssuedRefreshToken issueNewFamily(User user, DeviceContext device) {
        enforceConcurrentSessionLimit(user.getId());
        return issue(user, UUID.randomUUID(), device);
    }

    /**
     * Revokes whatever must go so that this login fits under the limit.
     *
     * <p>Reads the live families oldest-first and revokes from the front, keeping the
     * newest {@code max - 1} — one slot is reserved for the login about to happen.
     */
    private void enforceConcurrentSessionLimit(UUID userId) {
        List<RefreshTokenRepository.LiveFamily> families = refreshTokenRepository.findLiveFamilies(userId);
        int slotsForExisting = maxConcurrentSessions - 1;
        if (families.size() <= slotsForExisting) {
            return;
        }
        families.stream()
            .limit((long) families.size() - slotsForExisting)
            .forEach(family -> revokeFamilyInCurrentTransaction(family.getFamilyId()));
    }

    private IssuedRefreshToken issue(User user, UUID familyId, DeviceContext device) {
        String rawToken = TokenHasher.randomUrlSafeToken(TOKEN_BYTE_LENGTH);
        Instant now = Instant.now();
        RefreshToken token = RefreshToken.builder()
            .user(user)
            .tokenHash(TokenHasher.sha256Hex(rawToken))
            .familyId(familyId)
            .expiresAt(now.plus(REFRESH_TOKEN_TTL))
            .deviceLabel(device.deviceLabel())
            .createdIp(device.ip())
            // Seeded at creation, not left null: a family that logs in and goes quiet is
            // still ordered correctly against one that has been rotating for days.
            .lastUsedAt(now)
            .build();
        refreshTokenRepository.save(token);
        return new IssuedRefreshToken(rawToken, token);
    }

    /**
     * Validates a presented raw refresh token and rotates it.
     *
     * <p>A revoked token is not one situation but several, and they must not be answered
     * alike. Replaying a token that was superseded by its own successor is genuine reuse
     * and burns the family. Presenting one that was revoked by a newer login is the
     * anti-sharing policy doing its job, and the user needs to be told that in words —
     * burning the family again there would be noise, and answering "invalid" would leave
     * the UI showing a generic expiry for something quite specific.
     */
    @Transactional
    public RotationResult rotate(String rawToken) {
        Optional<RefreshToken> maybeToken = refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken));
        if (maybeToken.isEmpty()) {
            return new RotationResult.Invalid();
        }
        RefreshToken token = maybeToken.get();
        if (token.getRevokedAt() != null) {
            return afterRevocation(token);
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            return new RotationResult.Invalid();
        }
        Instant now = Instant.now();
        token.setRevokedAt(now);
        token.setRevokedReason(RevocationReason.ROTATED);
        refreshTokenRepository.save(token);
        IssuedRefreshToken issued = issue(token.getUser(), token.getFamilyId(),
            new DeviceContext(token.getDeviceLabel(), token.getCreatedIp()));
        return new RotationResult.Rotated(issued);
    }

    private RotationResult afterRevocation(RefreshToken token) {
        RevocationReason reason = token.getRevokedReason();
        if (reason == RevocationReason.SUPERSEDED) {
            // Already dealt with by the login that evicted it. Say so, plainly.
            return new RotationResult.Superseded();
        }
        if (reason == RevocationReason.LOGOUT || reason == RevocationReason.PASSWORD_CHANGE
            || reason == RevocationReason.ADMIN) {
            // Ended deliberately and knowingly. A stale tab hitting this is an ordinary
            // expired session, not an incident — treating it as reuse would raise an alarm
            // about the user's own click.
            return new RotationResult.Invalid();
        }
        // ROTATED, or null on a row revoked before V11 where the reason is genuinely
        // unknown. Conservative: assume the worst and burn the family.
        self.revokeFamily(token.getFamilyId(), RevocationReason.REUSE);
        return new RotationResult.ReuseDetected();
    }

    /**
     * Revokes a family in its OWN transaction, so the revocation survives the caller
     * rolling back — {@link AuthService#refresh} goes on to throw after reuse detection.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(UUID familyId, RevocationReason reason) {
        revokeFamilyInCurrentTransaction(familyId, reason);
    }

    /**
     * The same work without forcing a new transaction — for callers already inside one
     * that must commit or roll back together with the revocation, such as the eviction
     * that happens as part of issuing a login's family.
     */
    private void revokeFamilyInCurrentTransaction(UUID familyId) {
        revokeFamilyInCurrentTransaction(familyId, RevocationReason.SUPERSEDED);
    }

    private void revokeFamilyInCurrentTransaction(UUID familyId, RevocationReason reason) {
        List<RefreshToken> active = refreshTokenRepository.findByFamilyIdAndRevokedAtIsNull(familyId);
        stamp(active, reason);
    }

    @Transactional
    public void revokeToken(String rawToken, RevocationReason reason) {
        refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken)).ifPresent(t -> {
            if (t.getRevokedAt() == null) {
                stamp(List.of(t), reason);
            }
        });
    }

    @Transactional
    public void revokeAllForUser(UUID userId, RevocationReason reason) {
        stamp(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId), reason);
    }

    /** Number of live families revoked — what the admin action reports back. */
    @Transactional
    public int revokeActiveSessions(UUID userId, RevocationReason reason) {
        List<RefreshToken> active = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        long families = active.stream().map(RefreshToken::getFamilyId).distinct().count();
        stamp(active, reason);
        return (int) families;
    }

    private void stamp(List<RefreshToken> tokens, RevocationReason reason) {
        if (tokens.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        tokens.forEach(t -> {
            t.setRevokedAt(now);
            t.setRevokedReason(reason);
        });
        refreshTokenRepository.saveAll(tokens);
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

        /** Evicted by a newer login. The user gets an explanation, not a generic expiry. */
        record Superseded() implements RotationResult {
        }

        record Invalid() implements RotationResult {
        }
    }
}
