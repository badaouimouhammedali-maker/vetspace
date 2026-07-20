package com.vetspace.auth;

import com.vetspace.domain.user.EmailVerificationToken;
import com.vetspace.domain.user.User;
import com.vetspace.extras.DailyUserRateLimiter;
import com.vetspace.mail.AppMailSender;
import com.vetspace.repository.EmailVerificationTokenRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Issues and consumes email-verification tokens.
 *
 * <p>Tokens are single-use, expire after {@link #TOKEN_TTL}, and only ever reach the
 * database as a SHA-256 hash, so a leaked database dump cannot be replayed into account
 * takeovers.
 *
 * <p>{@code AUTO_VERIFY_EMAILS=true} marks new accounts verified on creation and skips the
 * mail entirely. That is how dev and the e2e stack keep working without a mailbox — it must
 * never be set in production, where it would defeat the whole control.
 */
@Service
public class EmailVerificationService {

    static final Duration TOKEN_TTL = Duration.ofHours(24);
    private static final int TOKEN_BYTE_LENGTH = 32;
    /** Resend cap: 3 per hour per account. */
    static final int RESEND_LIMIT_PER_HOUR = 3;
    private static final String RATE_SCOPE = "email-verification-resend";

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final AppMailSender mailSender;
    private final DailyUserRateLimiter rateLimiter;
    private final String frontendUrl;
    private final boolean autoVerify;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                                    UserRepository userRepository,
                                    AppMailSender mailSender,
                                    DailyUserRateLimiter rateLimiter,
                                    @Value("${app.frontend-url}") String frontendUrl,
                                    @Value("${app.auth.auto-verify-emails:false}") boolean autoVerify) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.rateLimiter = rateLimiter;
        this.frontendUrl = frontendUrl;
        this.autoVerify = autoVerify;
    }

    /** True when new registrations should be verified immediately (dev/e2e only). */
    public boolean isAutoVerifyEnabled() {
        return autoVerify;
    }

    /** Called right after registration. No-op when auto-verify is on. */
    @Transactional
    public void sendVerification(User user) {
        if (autoVerify) {
            return;
        }
        issueAndSend(user);
    }

    /**
     * Resend for a user who has not verified yet, rate-limited per account.
     *
     * <p>Returns silently whether or not the address matches an unverified account: a
     * different response would confirm which addresses are registered.
     */
    @Transactional
    public void resend(String email) {
        userRepository.findByEmailIgnoreCase(email)
            .filter(user -> !user.isEmailVerified())
            .ifPresent(user -> {
                rateLimiter.check(RATE_SCOPE, user.getId(), RESEND_LIMIT_PER_HOUR, Duration.ofHours(1));
                // One live token at a time, so an old link cannot be used after a resend.
                tokenRepository.deleteByUserId(user.getId());
                issueAndSend(user);
            });
    }

    /** Consumes a token and marks the account verified. */
    @Transactional
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken))
            .filter(t -> t.getUsedAt() == null)
            .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Lien de vérification invalide ou expiré"));

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        tokenRepository.save(token);
    }

    private void issueAndSend(User user) {
        String rawToken = TokenHasher.randomUrlSafeToken(TOKEN_BYTE_LENGTH);
        tokenRepository.save(EmailVerificationToken.builder()
            .user(user)
            .tokenHash(TokenHasher.sha256Hex(rawToken))
            .expiresAt(Instant.now().plus(TOKEN_TTL))
            .build());

        String link = frontendUrl + "/verify-email?token=" + rawToken;
        // The raw token appears only in the email body — never in a log.
        mailSender.send(user.getEmail(), "Confirmez votre adresse e-mail VetSpace",
            "Bienvenue sur VetSpace ! Confirmez votre adresse en cliquant sur ce lien "
                + "(valable 24 heures) : " + link);
    }
}
