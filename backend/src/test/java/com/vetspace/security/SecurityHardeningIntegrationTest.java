package com.vetspace.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vetspace.domain.school.School;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.UserRepository;
import java.util.UUID;
import org.hamcrest.Matchers;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class SecurityHardeningIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("app.recaptcha.enabled", () -> false);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    private String studentToken;

    /** Endpoints that must reject anonymous callers with 401. */
    private static final String[] AUTHED_ENDPOINTS = {
        "/api/users/me", "/api/sessions", "/api/stats/overview", "/api/notes",
        "/api/labels", "/api/signals", "/api/notifications", "/api/admin/overview",
    };

    private static final String[] ADMIN_ENDPOINTS = {
        "/api/admin/overview", "/api/admin/users", "/api/admin/questions",
    };

    @BeforeEach
    void setUp() {
        School school = schoolRepository.save(School.builder().name("ENSV").slug("ensv-" + UUID.randomUUID()).build());
        User student = userRepository.save(User.builder()
            .email("student-" + UUID.randomUUID() + "@example.com")
            .username("u" + UUID.randomUUID().toString().substring(0, 12))
            .passwordHash(passwordEncoder.encode("motdepasse-secret1"))
            .lastName("T").firstName("Student")
            .role(Role.STUDENT).status(UserStatus.ACTIVE)
            .school(school).studyYear(3)
            .build());
        studentToken = jwtService.generateAccessToken(student);
    }

    @Test
    void securityHeadersPresentOnEveryResponse() throws Exception {
        mockMvc.perform(get("/api/ping"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
            .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
    }

    @Test
    void tokenlessRequestsAreUnauthorized() throws Exception {
        for (String path : AUTHED_ENDPOINTS) {
            mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void studentIsForbiddenOnAdminRoutes() throws Exception {
        for (String path : ADMIN_ENDPOINTS) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void foreignOrUnknownOwnedResourceIdsReadAsNotFound() throws Exception {
        UUID foreign = UUID.randomUUID();
        mockMvc.perform(get("/api/sessions/" + foreign + "/play")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/notes/" + foreign)
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/labels/" + foreign + "/questions")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void oversizedNonMultipartBodyIsRejected() throws Exception {
        String huge = "{\"email\":\"a@b.com\",\"password\":\"" + "x".repeat(2_200_000) + "\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(huge))
            .andExpect(status().isPayloadTooLarge());
    }
}
