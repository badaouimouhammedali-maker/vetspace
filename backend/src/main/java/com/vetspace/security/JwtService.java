package com.vetspace.security;

import com.vetspace.domain.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Signs and verifies short-lived access tokens. Stateless: never persisted, never checked against the DB. */
@Component
public class JwtService {

    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    private final SecretKey key;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        // Derive a fixed-size 256-bit HMAC key regardless of the configured secret's raw length,
        // so a short dev value (e.g. "change-me") doesn't blow up jjwt's minimum key size check.
        this.key = Keys.hmacShaKeyFor(sha256(secret));
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ACCESS_TOKEN_TTL)))
            .signWith(key)
            .compact();
    }

    public Jws<Claims> parse(String token) throws JwtException {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
