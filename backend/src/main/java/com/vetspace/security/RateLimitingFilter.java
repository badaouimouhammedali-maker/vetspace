package com.vetspace.security;

import com.vetspace.web.error.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-IP request throttling on the auth endpoints most exposed to brute-forcing/abuse. In-memory: fine for a single instance. */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Bandwidth AUTH_LIMIT = Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)));
    private static final Bandwidth FORGOT_PASSWORD_LIMIT = Bandwidth.classic(5, Refill.intervally(5, Duration.ofHours(1)));
    private static final Set<String> AUTH_PATHS = Set.of("/api/auth/login", "/api/auth/register");
    private static final String FORGOT_PASSWORD_PATH = "/api/auth/forgot-password";

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public RateLimitingFilter(ObjectMapper objectMapper,
                              @Value("${app.rate-limits-enabled:true}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Bandwidth limit = enabled && "POST".equalsIgnoreCase(request.getMethod())
            ? limitFor(request.getRequestURI()) : null;
        if (limit != null) {
            String key = request.getRequestURI() + ":" + clientIp(request);
            Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder().addLimit(limit).build());
            if (!bucket.tryConsume(1)) {
                writeTooManyRequests(response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** Clears all counters; test-only hook so rate-limit assertions don't depend on execution order across a shared Spring context. */
    public void resetForTests() {
        buckets.clear();
    }

    private Bandwidth limitFor(String path) {
        if (AUTH_PATHS.contains(path)) {
            return AUTH_LIMIT;
        }
        if (FORGOT_PASSWORD_PATH.equals(path)) {
            return FORGOT_PASSWORD_LIMIT;
        }
        return null;
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError("Too Many Requests", "Rate limit exceeded", Instant.now());
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
