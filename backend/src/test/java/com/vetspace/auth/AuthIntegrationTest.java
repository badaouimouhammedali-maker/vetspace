package com.vetspace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetspace.domain.school.School;
import com.vetspace.mail.AppMailSender;
import com.vetspace.repository.RefreshTokenRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.security.RateLimitingFilter;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AuthIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("app.recaptcha.enabled", () -> false);
        // Registration now sends a verification email and login requires a verified
        // address; these suites predate that and are not testing it.
        registry.add("app.auth.auto-verify-emails", () -> true);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private RecordingMailSender mailSender;

    private UUID schoolId;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.resetForTests();
        School school = schoolRepository.save(School.builder()
            .name("ENSV Alger")
            .slug("ensv-alger-" + UUID.randomUUID())
            .build());
        schoolId = school.getId();
    }

    // ---------------------------------------------------------------
    // register -> login -> me
    // ---------------------------------------------------------------

    @Test
    void registerThenLoginThenMe() throws Exception {
        String email = nextEmail();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email, "student_" + email.hashCode(), "correct-horse-battery")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.role").value("STUDENT"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.accessToken").doesNotExist());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email, "correct-horse-battery")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.expiresInSeconds").value(900))
            .andExpect(cookie().httpOnly("refresh_token", true))
            .andExpect(cookie().path("refresh_token", "/api/auth"))
            .andReturn();

        String accessToken = readAccessToken(loginResult);

        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.activeSubscriptions").isArray())
            .andExpect(jsonPath("$.activeSubscriptions").isEmpty());
    }

    // ---------------------------------------------------------------
    // duplicate email / username
    // ---------------------------------------------------------------

    @Test
    void duplicateEmailAndUsernameAreRejected() throws Exception {
        String email = nextEmail();
        String username = "dup_" + email.hashCode();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email, username, "correct-horse-battery")))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email, "someone_else_" + email.hashCode(), "correct-horse-battery")))
            .andExpect(status().isConflict());

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(nextEmail(), username, "correct-horse-battery")))
            .andExpect(status().isConflict());
    }

    // ---------------------------------------------------------------
    // generic auth failures
    // ---------------------------------------------------------------

    @Test
    void wrongPasswordAndUnknownEmailReturnTheSameGenericUnauthorized() throws Exception {
        String email = nextEmail();
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email, "wrongpass_" + email.hashCode(), "correct-horse-battery")))
            .andExpect(status().isCreated());

        MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email, "totally-wrong-password")))
            .andExpect(status().isUnauthorized())
            .andReturn();

        MvcResult unknownEmail = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("nobody-" + System.nanoTime() + "@example.com", "whatever12345")))
            .andExpect(status().isUnauthorized())
            .andReturn();

        String messageA = objectMapper.readTree(wrongPassword.getResponse().getContentAsString()).get("message").asText();
        String messageB = objectMapper.readTree(unknownEmail.getResponse().getContentAsString()).get("message").asText();
        assertThat(messageA).isEqualTo(messageB);
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---------------------------------------------------------------
    // refresh rotation + reuse detection
    // ---------------------------------------------------------------

    @Test
    void refreshRotatesAndReuseRevokesTheWholeFamily() throws Exception {
        String email = nextEmail();
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email, "refresh_" + email.hashCode(), "correct-horse-battery")))
            .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email, "correct-horse-battery")))
            .andExpect(status().isOk())
            .andReturn();
        Cookie firstRefreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(firstRefreshCookie).isNotNull();

        MvcResult firstRefresh = mockMvc.perform(post("/api/auth/refresh").cookie(firstRefreshCookie))
            .andExpect(status().isOk())
            .andReturn();
        Cookie secondRefreshCookie = firstRefresh.getResponse().getCookie("refresh_token");
        assertThat(secondRefreshCookie).isNotNull();
        assertThat(secondRefreshCookie.getValue()).isNotEqualTo(firstRefreshCookie.getValue());

        // Reuse of the already-rotated first token: detected, whole family revoked.
        mockMvc.perform(post("/api/auth/refresh").cookie(firstRefreshCookie))
            .andExpect(status().isUnauthorized());

        // The second (legitimately rotated) token is now dead too, because the family was revoked.
        mockMvc.perform(post("/api/auth/refresh").cookie(secondRefreshCookie))
            .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // password reset flow
    // ---------------------------------------------------------------

    @Test
    void passwordResetIsSingleUseAndRevokesExistingRefreshTokens() throws Exception {
        String email = nextEmail();
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email, "reset_" + email.hashCode(), "correct-horse-battery")))
            .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email, "correct-horse-battery")))
            .andExpect(status().isOk())
            .andReturn();
        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))))
            .andExpect(status().isOk());

        String resetToken = extractResetToken(mailSender.last().body());

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", resetToken, "newPassword", "brand-new-password-1"))))
            .andExpect(status().isOk());

        // Single-use: replaying the same token fails.
        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", resetToken, "newPassword", "another-password-2"))))
            .andExpect(status().isBadRequest());

        // Old refresh token issued before the reset must be dead now.
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
            .andExpect(status().isUnauthorized());

        // Old password no longer works, new one does.
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email, "correct-horse-battery")))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email, "brand-new-password-1")))
            .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // rate limiting
    // ---------------------------------------------------------------

    @Test
    void loginIsRateLimitedPerIp() throws Exception {
        String email = nextEmail();
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson(email, "whatever-wrong-password")))
                .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email, "whatever-wrong-password")))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void forgotPasswordIsRateLimitedPerIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("email", nextEmail()))))
                .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", nextEmail()))))
            .andExpect(status().isTooManyRequests());
    }

    // ---------------------------------------------------------------
    // weak password
    // ---------------------------------------------------------------

    @Test
    void weakPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(nextEmail(), "weak_" + System.nanoTime(), "short1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsStringIgnoringCase("password")));
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private String nextEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String registerJson(String email, String username, String password) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("lastName", "Doe");
        body.put("firstName", "Jane");
        body.put("username", username);
        body.put("email", email);
        body.put("password", password);
        body.put("schoolId", schoolId.toString());
        body.put("studyYear", 3);
        body.put("recaptchaToken", "unused-in-tests");
        return objectMapper.writeValueAsString(body);
    }

    private String loginJson(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    private String readAccessToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private String extractResetToken(String mailBody) {
        Matcher matcher = TOKEN_PATTERN.matcher(mailBody);
        if (!matcher.find()) {
            throw new IllegalStateException("No reset token found in mail body: " + mailBody);
        }
        return matcher.group(1);
    }

    @TestConfiguration
    static class MailTestConfig {
        @Bean
        @Primary
        RecordingMailSender recordingMailSender() {
            return new RecordingMailSender();
        }
    }

    static class RecordingMailSender implements AppMailSender {
        private final List<SentMail> sent = new CopyOnWriteArrayList<>();

        record SentMail(String to, String subject, String body) {
        }

        @Override
        public void send(String to, String subject, String body) {
            sent.add(new SentMail(to, subject, body));
        }

        SentMail last() {
            return sent.get(sent.size() - 1);
        }
    }
}
