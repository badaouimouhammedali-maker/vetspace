package com.vetspace.auth;

import com.vetspace.auth.dto.ForgotPasswordRequest;
import com.vetspace.auth.dto.LoginRequest;
import com.vetspace.auth.dto.LoginResponse;
import com.vetspace.auth.dto.RegisterRequest;
import com.vetspace.auth.dto.RegisterResponse;
import com.vetspace.auth.dto.ResendVerificationRequest;
import com.vetspace.auth.dto.ResetPasswordRequest;
import com.vetspace.auth.dto.VerifyEmailRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService,
                           PasswordResetService passwordResetService,
                           EmailVerificationService emailVerificationService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * The HttpServletRequest is here only to derive the device label and origin IP that
     * get stored with the refresh-token family — never taken from the body, where a
     * client could dress its session up as someone else's device in the admin list.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest httpRequest) {
        AuthService.LoginResult result =
            authService.login(request.email(), request.password(), DeviceContext.from(httpRequest));
        return withRefreshCookie(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
        @CookieValue(name = RefreshTokenService.COOKIE_NAME, required = false) String refreshToken) {
        AuthService.LoginResult result = authService.refresh(refreshToken);
        return withRefreshCookie(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @CookieValue(name = RefreshTokenService.COOKIE_NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        ResponseCookie cookie = refreshTokenService.expiredCookie();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.ok().build();
    }

    /**
     * Always 200, whether or not the address matches an unverified account — a different
     * response would confirm which addresses are registered. Rate-limited per account.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resend(request.email());
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<LoginResponse> withRefreshCookie(AuthService.LoginResult result) {
        ResponseCookie cookie = refreshTokenService.buildCookie(result.rawRefreshToken());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(new LoginResponse(result.accessToken(), result.accessTokenTtl().toSeconds()));
    }
}
