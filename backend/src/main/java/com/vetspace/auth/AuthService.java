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
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RecaptchaVerifier recaptchaVerifier;

    public AuthService(UserRepository userRepository, SchoolRepository schoolRepository, PasswordEncoder passwordEncoder,
                        JwtService jwtService, RefreshTokenService refreshTokenService, RecaptchaVerifier recaptchaVerifier) {
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.recaptchaVerifier = recaptchaVerifier;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (!recaptchaVerifier.verify(request.recaptchaToken())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reCAPTCHA verification failed");
        }
        if (userRepository.findByEmailIgnoreCase(request.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already in use");
        }
        School school = schoolRepository.findById(request.schoolId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown school"));

        User user = User.builder()
            .email(request.email())
            .username(request.username())
            .passwordHash(passwordEncoder.encode(request.password()))
            .lastName(request.lastName())
            .firstName(request.firstName())
            .role(Role.STUDENT)
            .status(UserStatus.ACTIVE)
            .school(school)
            .studyYear(request.studyYear())
            .build();
        user = userRepository.save(user);
        return new RegisterResponse(user.getId(), user.getEmail(), user.getUsername(), user.getRole(), user.getStatus());
    }

    @Transactional
    public LoginResult login(String email, String password) {
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
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
