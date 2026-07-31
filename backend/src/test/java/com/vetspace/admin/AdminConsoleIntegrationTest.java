package com.vetspace.admin;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.vetspace.repository.ModuleRepository;
import com.vetspace.repository.PackRepository;
import com.vetspace.repository.PropositionRepository;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.SubscriptionRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.JwtService;
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
class AdminConsoleIntegrationTest {

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

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private ModuleRepository moduleRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private PropositionRepository propositionRepository;
    @Autowired private PackRepository packRepository;
    @Autowired private ActivationCodeRepository activationCodeRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    private School school;
    private Question question;
    private User student;
    private String studentPassword;
    private String studentToken;
    private String adminToken;
    private User admin;
    private String teacherToken;

    @BeforeEach
    void setUp() {
        school = schoolRepository.save(School.builder().name("ENSV").slug("ensv-" + UUID.randomUUID()).build());
        Module module = moduleRepository.save(Module.builder()
            .studyYear(3).name(moduleName).position(1).published(true).build());
        Course course = courseRepository.save(Course.builder()
            .module(module).name("Ostéologie").position(1).published(true).build());
        question = questionRepository.save(Question.builder()
            .course(course).statement("Q?").published(true).build());
        propositionRepository.save(Proposition.builder()
            .question(question).letter("A").text("Vrai").isTrue(true).position(1).build());

        studentPassword = "motdepasse-secret1";
        student = newStudent(school, 3, studentPassword);
        studentToken = jwtService.generateAccessToken(student);

        admin = userRepository.save(user(Role.ADMIN, school, null, "motdepasse-admin1"));
        adminToken = jwtService.generateAccessToken(admin);
        teacherToken = jwtService.generateAccessToken(
            userRepository.save(user(Role.TEACHER, school, null, "motdepasse-teach1")));
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

    @Test
    void overviewCountsAndStudentIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());

        // Overview is a global aggregate; assert it reflects at least the data this test created.
        mockMvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.students").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.questions").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.activeSubscriptions").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.openSignals").value(greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.latestRegistrations[*].email", hasItem(student.getEmail())));
    }

    /**
     * The breakdown is what keeps "école" earning its place on the signup form now that
     * it scopes nothing: it is the one screen where the field does work.
     */
    @Test
    void overviewBreaksStudentsDownBySchool() throws Exception {
        mockMvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.studentsBySchool").isArray())
            // This test's own student belongs to `school`, so that row must be there and
            // must count at least them.
            .andExpect(jsonPath("$.studentsBySchool[?(@.schoolName == '" + school.getName() + "')]").exists())
            .andExpect(jsonPath("$.studentsBySchool[?(@.schoolName == '" + school.getName() + "')].students")
                .value(hasItem(greaterThanOrEqualTo(1))));
    }

    @Test
    void userSearchAndFilter() throws Exception {
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.content[*].email", hasItem(student.getEmail())));

        // Search by the student's unique username returns exactly that student; a miss returns nothing.
        mockMvc.perform(get("/api/admin/users").param("query", student.getUsername())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].email").value(student.getEmail()))
            .andExpect(jsonPath("$.content[0].activeSubscriptions").value(1));
        mockMvc.perform(get("/api/admin/users").param("query", "zzz-no-match")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void disableBlocksLoginAndIsAdminOnly() throws Exception {
        // Teacher cannot toggle status (ADMIN only).
        mockMvc.perform(patch("/api/admin/users/" + student.getId() + "/status")
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "DISABLED"))))
            .andExpect(status().isForbidden());

        // Admin cannot disable self.
        mockMvc.perform(patch("/api/admin/users/" + admin.getId() + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "DISABLED"))))
            .andExpect(status().isConflict());

        // Admin disables the student.
        mockMvc.perform(patch("/api/admin/users/" + student.getId() + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "DISABLED"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DISABLED"));

        // Disabled account cannot log in — generic 401.
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", student.getEmail(), "password", studentPassword))))
            .andExpect(status().isUnauthorized());

        // Re-enabling restores login.
        mockMvc.perform(patch("/api/admin/users/" + student.getId() + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "ACTIVE"))))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", student.getEmail(), "password", studentPassword))))
            .andExpect(status().isOk());
    }

    @Test
    void supportInboxIsStaffOnly() throws Exception {
        mockMvc.perform(post("/api/support")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("subject", "Bug", "body", "Ça plante"))))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/support").header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/support").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].subject").value("Bug"))
            .andExpect(jsonPath("$.content[0].userEmail").value(student.getEmail()));
    }

    @Test
    void notificationHistoryShowsTargeting() throws Exception {
        mockMvc.perform(post("/api/admin/notifications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("kind", "INFO", "title", "Pour tous", "body", "b"))))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/notifications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("kind", "UPDATE", "title", "Ciblé", "body", "b",
                    "schoolId", school.getId().toString(), "studyYear", 3))))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/notifications").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].title").value("Ciblé"))
            .andExpect(jsonPath("$[0].schoolName").value("ENSV"))
            .andExpect(jsonPath("$[0].studyYear").value(3))
            .andExpect(jsonPath("$[1].title").value("Pour tous"))
            .andExpect(jsonPath("$[1].schoolName").doesNotExist());

        mockMvc.perform(get("/api/admin/notifications").header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }
}
