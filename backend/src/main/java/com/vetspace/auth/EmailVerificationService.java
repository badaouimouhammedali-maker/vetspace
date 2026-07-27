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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
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

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

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

    /**
     * Issues a token and tries to email it. Returns whether the message went out.
     *
     * <p>Deliberately NOT {@code @Transactional}, and deliberately called by
     * {@link AuthService#register} only after the account is committed. A mail server
     * being down is an operational problem; it must never cost a student their
     * registration. The token row is saved on its own (Spring Data's {@code save} carries
     * its own transaction), so it is durable before SMTP is ever dialled — which is what
     * makes "renvoyer" work afterwards.
     *
     * <p>Returns {@code true} under auto-verify: nothing needed sending and the account
     * is already verified, so reporting a failure would push the student towards a resend
     * button with nothing to resend.
     */
    public boolean trySendVerification(User user) {
        if (autoVerify) {
            return true;
        }
        return trySend(user, issueToken(user));
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
                // trySend swallows a mail failure, so the delete+insert above still
                // commits and the caller still gets the same opaque 200. Rolling back
                // here would leave the account with no token at all — strictly worse
                // than a token whose email did not arrive, since the student can simply
                // ask again.
                trySend(user, issueToken(user));
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

    /** Stores a fresh single-use token and returns the link that carries its raw value. */
    private String issueToken(User user) {
        String rawToken = TokenHasher.randomUrlSafeToken(TOKEN_BYTE_LENGTH);
        tokenRepository.save(EmailVerificationToken.builder()
            .user(user)
            .tokenHash(TokenHasher.sha256Hex(rawToken))
            .expiresAt(Instant.now().plus(TOKEN_TTL))
            .build());
        return frontendUrl + "/verify-email?token=" + rawToken;
    }

    /**
     * Sends the verification mail, reporting failure rather than throwing.
     *
     * <p>The try block wraps the send and nothing else, so a bug in our own token code
     * still surfaces as a 500 — only the mail server's availability is treated as
     * survivable.
     */
    private boolean trySend(User user, String link) {
        try {
            // The raw token appears only in the email body — never in a log.
            mailSender.send(user.getEmail(), "Confirmez votre adresse e-mail VetSpace",
                "Bienvenue sur VetSpace ! Confirmez votre adresse en cliquant sur ce lien "
                    + "(valable 24 heures) : " + link);
            return true;
        } catch (MailException ex) {
            // userId, not the address: the log must not become a list of who registered.
            // The request id is on the line already — RequestIdFilter puts it in the MDC
            // and logging.pattern prints it — so a student's "Référence" leads here.
            log.error("Verification email could not be sent for user {}", user.getId(), ex);
            return false;
        }
    }
}
