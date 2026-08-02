package com.vetspace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetspace.domain.school.School;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.repository.RefreshTokenRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * MAX_CONCURRENT_SESSIONS above 1: the limit loosens, and eviction becomes
 * least-recently-used rather than everything-but-the-newest.
 *
 * <p>A class of its own because the property is read once at bean construction — changing
 * it inside a method would need a context restart anyway, and a separate class states the
 * configuration being tested in its name.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class ConcurrentSessionsLruIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("app.recaptcha.enabled", () -> false);
        registry.add("app.auth.auto-verify-emails", () -> true);
        registry.add("app.rate-limits-enabled", () -> false);
        registry.add("app.auth.max-concurrent-sessions", () -> 2);
    }

    private static final String PASSWORD = "MotDePasse!2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User student;
    private String email;

    @BeforeEach
    void setUp() {
        School school = schoolRepository.save(School.builder()
            .name("ENSV").slug("ensv-" + UUID.randomUUID()).build());
        email = "lru-" + UUID.randomUUID() + "@example.com";
        student = userRepository.save(User.builder()
            .email(email)
            .username("u" + UUID.randomUUID().toString().substring(0, 12))
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .lastName("T").firstName("S")
            .role(Role.STUDENT).status(UserStatus.ACTIVE)
            .school(school).studyYear(3)
            .emailVerified(true)
            .build());
    }

    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();
        return result.getResponse().getCookie(RefreshTokenService.COOKIE_NAME);
    }

    private Cookie refreshExpectingOk(Cookie cookie) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/refresh").cookie(cookie))
            .andExpect(status().isOk())
            .andReturn();
        return result.getResponse().getCookie(RefreshTokenService.COOKIE_NAME);
    }

    @Test
    void twoDevicesCoexist() throws Exception {
        Cookie first = login();
        Cookie second = login();

        assertThat(refreshTokenRepository.findLiveFamilies(student.getId())).hasSize(2);
        // Both still work — this is the loosened policy, not the strict one.
        refreshExpectingOk(first);
        refreshExpectingOk(second);
    }

    @Test
    void aThirdLoginEvictsTheLeastRecentlyUsedNotTheOldest() throws Exception {
        Cookie first = login();
        Thread.sleep(20);
        Cookie second = login();

        // Touch the FIRST device, making the second the least recently used even though
        // it is the newer login. Evicting by age alone would take the wrong one.
        Thread.sleep(20);
        Cookie firstRotated = refreshExpectingOk(first);

        Thread.sleep(20);
        Cookie third = login();

        assertThat(refreshTokenRepository.findLiveFamilies(student.getId())).hasSize(2);
        // The idle device is the one that lost its place...
        mockMvc.perform(post("/api/auth/refresh").cookie(second))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("SESSION_SUPERSEDED"));
        // ...while the device actually being used survived, as did the new one.
        refreshExpectingOk(firstRotated);
        refreshExpectingOk(third);
    }
}
