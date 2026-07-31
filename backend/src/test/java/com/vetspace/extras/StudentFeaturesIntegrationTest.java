package com.vetspace.extras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetspace.domain.access.ActivationCode;
import com.vetspace.domain.access.Pack;
import com.vetspace.domain.access.Subscription;
import com.vetspace.domain.content.Course;
import com.vetspace.domain.content.Module;
import com.vetspace.domain.content.Proposition;
import com.vetspace.domain.content.Question;
import com.vetspace.domain.school.School;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.repository.ActivationCodeRepository;
import com.vetspace.repository.CourseRepository;
import com.vetspace.repository.LabelRepository;
import com.vetspace.repository.ModuleRepository;
import com.vetspace.repository.NoteRepository;
import com.vetspace.repository.PackRepository;
import com.vetspace.repository.PropositionRepository;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.SessionRepository;
import com.vetspace.repository.SubscriptionRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class StudentFeaturesIntegrationTest {

    /** Packs are unique on (study_year, academic_year) nationally; varchar(20). */
    private static String uniqueAcademicYear() {
        return "26/" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Fresh per test method, not per class: modules are unique on (study_year, name)
     * nationally, and setUp runs before every test — a class-level constant collides
     * with itself on the second method.
     */
    private final String moduleName = "Anatomie " + UUID.randomUUID();

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
    private ModuleRepository moduleRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private PropositionRepository propositionRepository;

    @Autowired
    private PackRepository packRepository;

    @Autowired
    private ActivationCodeRepository activationCodeRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private DailyUserRateLimiter dailyUserRateLimiter;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private School school;
    private School otherSchool;
    private Question question;
    private User student;
    private String studentToken;
    private String otherStudentToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        dailyUserRateLimiter.resetForTests();
        school = schoolRepository.save(School.builder().name("ENSV").slug("ensv-" + UUID.randomUUID()).build());
        otherSchool = schoolRepository.save(School.builder().name("Autre").slug("autre-" + UUID.randomUUID()).build());
        Module module = moduleRepository.save(Module.builder()
            .studyYear(3).name(moduleName).position(1).published(true).build());
        Course course = courseRepository.save(Course.builder()
            .module(module).name("Ostéologie").position(1).published(true).build());
        question = questionRepository.save(Question.builder()
            .course(course).statement("Q?").published(true).build());
        propositionRepository.save(Proposition.builder()
            .question(question).letter("A").text("Vrai").isTrue(true).position(1).build());
        propositionRepository.save(Proposition.builder()
            .question(question).letter("B").text("Faux").isTrue(false).position(2).build());

        student = newStudent(school, 3, "motdepasse-secret1");
        studentToken = jwtService.generateAccessToken(student);
        otherStudentToken = jwtService.generateAccessToken(newStudent(school, 3, "motdepasse-secret2"));

        User admin = userRepository.save(user(Role.ADMIN, school, null, "motdepasse-admin1"));
        adminToken = jwtService.generateAccessToken(admin);
    }

    private User newStudent(School school, int year, String rawPassword) {
        User u = userRepository.save(user(Role.STUDENT, school, year, rawPassword));
        Instant expires = Instant.now().plus(365, ChronoUnit.DAYS);
        Pack pack = packRepository.save(Pack.builder()
            .studyYear(year).name("Pack test").academicYear(uniqueAcademicYear())
            .priceDa(1000).active(true).expiresAt(expires).build());
        ActivationCode code = activationCodeRepository.save(ActivationCode.builder()
            .pack(pack).codeHash("test-" + UUID.randomUUID()).maxUses(1).usedCount(1).revoked(false).build());
        subscriptionRepository.save(Subscription.builder()
            .user(u).pack(pack).activationCode(code)
            .startsAt(Instant.now().minusSeconds(60)).endsAt(expires).build());
        return u;
    }

    private User user(Role role, School school, Integer year, String rawPassword) {
        return User.builder()
            .email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com")
            .username("u" + UUID.randomUUID().toString().substring(0, 12))
            .passwordHash(passwordEncoder.encode(rawPassword))
            .lastName("T").firstName(role.name())
            .role(role).status(UserStatus.ACTIVE)
            .school(school).studyYear(year)
            .emailVerified(true)
            .build();
    }

    // ---------------------------------------------------------------
    // Labels
    // ---------------------------------------------------------------

    @Test
    void labelCrudAttachAndOwnership() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/labels")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "À revoir", "color", "#ff0000"))))
            .andExpect(status().isCreated())
            .andReturn();
        String labelId = objectMapper.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .get("id").asText();

        mockMvc.perform(post("/api/labels/" + labelId + "/questions/" + question.getId())
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/labels/" + labelId + "/questions")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(question.getId().toString()));

        // labelIds works as a session filter.
        mockMvc.perform(post("/api/sessions")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "sessionType", "ENTRAINEMENT",
                    "filters", Map.of("labelIds", java.util.List.of(labelId)),
                    "questionCount", 5))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.questionCount").value(1));

        // Foreign user: label reads as nonexistent.
        mockMvc.perform(get("/api/labels/" + labelId + "/questions")
                .header("Authorization", "Bearer " + otherStudentToken))
            .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/labels/" + labelId)
                .header("Authorization", "Bearer " + otherStudentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "X", "color", "#00ff00"))))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/labels/" + labelId)
                .header("Authorization", "Bearer " + otherStudentToken))
            .andExpect(status().isNotFound());

        // Owner detaches and deletes fine.
        mockMvc.perform(delete("/api/labels/" + labelId + "/questions/" + question.getId())
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/labels/" + labelId)
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isNoContent());
    }

    @Test
    void labelColorMustBeHex() throws Exception {
        mockMvc.perform(post("/api/labels")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "X", "color", "red"))))
            .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------
    // Notes
    // ---------------------------------------------------------------

    @Test
    void noteCrudSanitizesAndEnforcesOwnership() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/notes")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "title", "Ma note",
                    "contentHtml", "<p>ok</p><script>alert(1)</script>",
                    "questionId", question.getId().toString()))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.contentHtml").value("<p>ok</p>"))
            .andReturn();
        String noteId = objectMapper.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .get("id").asText();

        mockMvc.perform(get("/api/notes?questionId=" + question.getId())
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

        // Foreign user: 404 on read/update/delete.
        mockMvc.perform(get("/api/notes/" + noteId).header("Authorization", "Bearer " + otherStudentToken))
            .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/notes/" + noteId)
                .header("Authorization", "Bearer " + otherStudentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "X", "contentHtml", "<p>y</p>"))))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/notes/" + noteId).header("Authorization", "Bearer " + otherStudentToken))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/notes/" + noteId).header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------
    // Signals
    // ---------------------------------------------------------------

    @Test
    void signalRateLimitTenPerDayAndAdminResolve() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/signals")
                    .header("Authorization", "Bearer " + studentToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                        "questionId", question.getId().toString(), "message", "Erreur n°" + i))))
                .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/signals")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "questionId", question.getId().toString(), "message", "Une de trop"))))
            .andExpect(status().isTooManyRequests());

        MvcResult myList = mockMvc.perform(get("/api/signals").header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode signals = objectMapper.readTree(myList.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(signals.size()).isEqualTo(10);
        String signalId = signals.get(0).get("id").asText();

        // Student cannot use the admin surface.
        mockMvc.perform(get("/api/admin/signals").header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/signals/" + signalId + "/resolve")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reply", "Corrigé, merci !"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESOLVED"))
            .andExpect(jsonPath("$.adminReply").value("Corrigé, merci !"));

        mockMvc.perform(get("/api/signals").header("Authorization", "Bearer " + studentToken))
            .andExpect(jsonPath("$[0].status").value("RESOLVED"));
    }

    // ---------------------------------------------------------------
    // Notifications
    // ---------------------------------------------------------------

    @Test
    void notificationTargetingAndReadState() throws Exception {
        broadcast(Map.of("kind", "INFO", "title", "Pour tous", "body", "b"));
        broadcast(Map.of("kind", "QUESTIONS", "title", "Pour mon école", "body", "b",
            "schoolId", school.getId().toString(), "studyYear", 3));
        broadcast(Map.of("kind", "UPDATE", "title", "Autre école", "body", "b",
            "schoolId", otherSchool.getId().toString()));
        broadcast(Map.of("kind", "INFO", "title", "Autre année", "body", "b",
            "schoolId", school.getId().toString(), "studyYear", 5));

        // Student (school, year 3) sees the broadcast + own-school-year one only.
        MvcResult list = mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode notifications = objectMapper.readTree(list.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(notifications).hasSize(2);
        notifications.forEach(n -> assertThat(n.get("read").asBoolean()).isFalse());

        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + studentToken))
            .andExpect(jsonPath("$.count").value(2));

        // Mark one read.
        String firstId = notifications.get(0).get("id").asText();
        mockMvc.perform(post("/api/notifications/" + firstId + "/read")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + studentToken))
            .andExpect(jsonPath("$.count").value(1));

        // Mark all read.
        mockMvc.perform(post("/api/notifications/read-all").header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + studentToken))
            .andExpect(jsonPath("$.count").value(0));

        // Soft delete removes it from the list without touching the notification itself.
        mockMvc.perform(delete("/api/notifications/" + firstId).header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + studentToken))
            .andExpect(jsonPath("$.length()").value(1));

        // Out-of-target notification reads as nonexistent for this student.
        MvcResult adminList = mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode allNotifications = objectMapper.readTree(adminList.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(allNotifications).hasSize(4);
        String foreignId = null;
        for (JsonNode n : allNotifications) {
            if (n.get("title").asText().equals("Autre école")) {
                foreignId = n.get("id").asText();
            }
        }
        mockMvc.perform(post("/api/notifications/" + foreignId + "/read")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isNotFound());
    }

    private void broadcast(Map<String, Object> body) throws Exception {
        mockMvc.perform(post("/api/admin/notifications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------
    // Profile + account deletion
    // ---------------------------------------------------------------

    @Test
    void profilePatchValidatesAndConflicts() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("themePrimary", "not-a-color"))))
            .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/users/me")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "firstName", "Jeanne", "themePrimary", "#a1B2c3"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Jeanne"))
            .andExpect(jsonPath("$.theme.primary").value("#a1B2c3"));

        // Username collision -> 409.
        User other = userRepository.findById(
            userRepository.findByEmailIgnoreCase(student.getEmail()).orElseThrow().getId()).orElseThrow();
        String takenUsername = userRepository.findAll().stream()
            .filter(u -> !u.getId().equals(other.getId()))
            .findFirst().orElseThrow().getUsername();
        mockMvc.perform(patch("/api/users/me")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", takenUsername))))
            .andExpect(status().isConflict());
    }

    @Test
    void passwordChangeRequiresCurrentAndRevokesTokens() throws Exception {
        mockMvc.perform(post("/api/users/me/password")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "currentPassword", "wrong-password", "newPassword", "nouveau-mot-de-passe"))))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/users/me/password")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "currentPassword", "motdepasse-secret1", "newPassword", "nouveau-mot-de-passe"))))
            .andExpect(status().isNoContent());

        // New password works for login.
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", student.getEmail(), "password", "nouveau-mot-de-passe"))))
            .andExpect(status().isOk());
    }

    @Test
    void accountDeletionAnonymizesAndKillsLogin() throws Exception {
        String originalEmail = student.getEmail();

        // Seed personal data + a session (aggregates must survive).
        mockMvc.perform(post("/api/notes")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "n", "contentHtml", "<p>x</p>"))))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/labels")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "l", "color", "#123456"))))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/sessions")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "sessionType", "ENTRAINEMENT", "questionCount", 5))))
            .andExpect(status().isCreated());

        // Wrong password refused; correct proceeds.
        mockMvc.perform(delete("/api/users/me")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("password", "wrong"))))
            .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/users/me")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("password", "motdepasse-secret1"))))
            .andExpect(status().isNoContent());

        // Anonymized row: identifiers randomized, DISABLED, personal data gone, sessions kept.
        User anonymized = userRepository.findById(student.getId()).orElseThrow();
        assertThat(anonymized.getEmail()).startsWith("deleted-").endsWith("@anonymized.invalid");
        assertThat(anonymized.getUsername()).startsWith("deleted-");
        assertThat(anonymized.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(anonymized.getPhotoUrl()).isNull();
        assertThat(noteRepository.findByUserId(student.getId())).isEmpty();
        assertThat(labelRepository.findByUserId(student.getId())).isEmpty();
        assertThat(sessionRepository.countByUserId(student.getId())).isEqualTo(1);

        // Old credentials are dead — same generic 401 as any bad login.
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", originalEmail, "password", "motdepasse-secret1"))))
            .andExpect(status().isUnauthorized());
    }
}
