package com.vetspace.auth;

import com.vetspace.auth.dto.RegisterRequest;
import com.vetspace.auth.dto.RegisterResponse;
import com.vetspace.domain.school.School;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.JwtService;
import com.vetspace.security.RecaptchaVerifier;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RecaptchaVerifier recaptchaVerifier;
    private final LoginAttemptService loginAttemptService;
    private final EmailVerificationService emailVerificationService;
    private final TransactionTemplate transactionTemplate;

    public AuthService(UserRepository userRepository, SchoolRepository schoolRepository, PasswordEncoder passwordEncoder,
                        JwtService jwtService, RefreshTokenService refreshTokenService, RecaptchaVerifier recaptchaVerifier,
                        LoginAttemptService loginAttemptService,
                        EmailVerificationService emailVerificationService,
                        TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.recaptchaVerifier = recaptchaVerifier;
        this.loginAttemptService = loginAttemptService;
        this.emailVerificationService = emailVerificationService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Creates the account, then tries to email the verification link.
     *
     * <p>Note what is NOT annotated: this method. It used to be {@code @Transactional}
     * with the mail send inside, so an SMTP outage rolled the whole registration back and
     * answered 500 — the student ended up with no account, no email and no way forward,
     * for a failure that had nothing to do with them.
     *
     * <p>Now the persistence runs in its own transaction via {@link TransactionTemplate}
     * and commits before a single byte goes near the mail server. The account survives a
     * mail outage, and because the send happens inside the request rather than in an
     * {@code AFTER_COMMIT} listener, its outcome is still known in time to be reported in
     * the response — which is what lets the UI offer "renvoyer" instead of sending
     * someone to an inbox that will stay empty. An {@code AFTER_COMMIT} listener would
     * fire after this method returns, so the flag could only ever have been a guess.
     *
     * <p>The reCAPTCHA call also moves out of the transaction: it is a network round trip
     * to Google, and it was holding a database connection open for its duration.
     */
    public RegisterResponse register(RegisterRequest request) {
        if (!recaptchaVerifier.verify(request.recaptchaToken())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reCAPTCHA verification failed");
        }

        User user = transactionTemplate.execute(status -> persistNewUser(request));

        boolean emailSent = emailVerificationService.trySendVerification(user);
        return new RegisterResponse(user.getId(), user.getEmail(), user.getUsername(), user.getRole(),
            user.getStatus(), user.isEmailVerified(), emailSent);
    }

    /** The part that must be atomic: uniqueness checks and the insert. */
    private User persistNewUser(RegisterRequest request) {
        if (userRepository.findByEmailIgnoreCase(request.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already in use");
        }
        School school = schoolRepository.findById(request.schoolId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown school"));

        return userRepository.save(User.builder()
            .email(request.email())
            .username(request.username())
            .passwordHash(passwordEncoder.encode(request.password()))
            .lastName(request.lastName())
            .firstName(request.firstName())
            .role(Role.STUDENT)
            .status(UserStatus.ACTIVE)
            // AUTO_VERIFY_EMAILS short-circuits the whole flow for dev and e2e, where
            // there is no mailbox to click through. Never enabled in production.
            .emailVerified(emailVerificationService.isAutoVerifyEnabled())
            .school(school)
            .studyYear(request.studyYear())
            .build());
    }

    /**
     * One message for every failure mode — unknown email, wrong password, disabled account,
     * locked account. Anything more specific is an oracle: it would let an attacker confirm
     * which addresses are registered, and tell them exactly when a lockout has lapsed.
     */
    static final String INVALID_CREDENTIALS =
        "Identifiants invalides ou compte temporairement verrouillé";

    /** Signals the dedicated "confirm your address" screen; see the login flow below. */
    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";

    @Transactional
    public LoginResult login(String email, String password) {
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        if (loginAttemptService.isLocked(user)) {
            // Checked before the password so a locked account cannot be probed for the
            // correct password, and the response is identical either way.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptService.recordFailure(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        loginAttemptService.clear(user);
        if (!user.isEmailVerified()) {
            // Deliberately distinct from INVALID_CREDENTIALS, and only reachable AFTER the
            // password matched — so it tells an attacker nothing they did not already know,
            // while letting the UI offer "resend the link" instead of a dead end.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, EMAIL_NOT_VERIFIED);
        }
        String accessToken = jwtService.generateAccessToken(user);
        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.issueNewFamily(user);
        return new LoginResult(accessToken, JwtService.ACCESS_TOKEN_TTL, issued.rawValue());
    }

    @Transactional
    public LoginResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing refresh token");
        }
        RefreshTokenService.RotationResult result = refreshTokenService.rotate(rawRefreshToken);
        if (result instanceof RefreshTokenService.RotationResult.Rotated rotated) {
            User user = rotated.issued().entity().getUser();
            String accessToken = jwtService.generateAccessToken(user);
            return new LoginResult(accessToken, JwtService.ACCESS_TOKEN_TTL, rotated.issued().rawValue());
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revokeToken(rawRefreshToken);
        }
    }

    public record LoginResult(String accessToken, Duration accessTokenTtl, String rawRefreshToken) {
    }
}
