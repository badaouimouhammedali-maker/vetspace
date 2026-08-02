package com.vetspace.auth;

import com.vetspace.domain.user.RevocationReason;
import com.vetspace.domain.user.PasswordResetToken;
import com.vetspace.domain.user.User;
import com.vetspace.mail.AppMailSender;
import com.vetspace.repository.PasswordResetTokenRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AppMailSender mailSender;
    private final String frontendUrl;

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository passwordResetTokenRepository,
                                 RefreshTokenService refreshTokenService, PasswordEncoder passwordEncoder,
                                 AppMailSender mailSender, @Value("${app.frontend-url}") String frontendUrl) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
    }

    /** Always succeeds from the caller's point of view, regardless of whether the email matches an account (no user enumeration). */
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(this::sendResetLink);
    }

    /**
     * Issues a reset token and tries to email it.
     *
     * <p>A mail failure is logged and swallowed. Two reasons it is not reported to the
     * caller, unlike registration: the token row still commits, so a retry works; and
     * this endpoint answers identically whether or not the address matches an account.
     * Surfacing "the send failed" would break that — a failure can only be reported for
     * an address that exists, which turns the response into the user-enumeration oracle
     * the opaque contract exists to prevent.
     */
    private void sendResetLink(User user) {
        String rawToken = TokenHasher.randomUrlSafeToken(TOKEN_BYTE_LENGTH);
        PasswordResetToken token = PasswordResetToken.builder()
            .user(user)
            .tokenHash(TokenHasher.sha256Hex(rawToken))
            .expiresAt(Instant.now().plus(RESET_TOKEN_TTL))
            .build();
        passwordResetTokenRepository.save(token);
        String link = frontendUrl + "/reset-password?token=" + rawToken;
        try {
            mailSender.send(user.getEmail(), "Réinitialisation de votre mot de passe VetSpace",
                "Cliquez sur ce lien pour choisir un nouveau mot de passe : " + link);
        } catch (MailException ex) {
            // userId, never the address or the token. The request id is already on the
            // line via the MDC, so a support report leads straight here.
            log.error("Password reset email could not be sent for user {}", user.getId(), ex);
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken))
            .filter(t -> t.getUsedAt() == null)
            .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);

        refreshTokenService.revokeAllForUser(user.getId(), RevocationReason.PASSWORD_CHANGE);
    }
}
