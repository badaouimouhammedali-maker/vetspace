package com.vetspace.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vetspace.domain.access.ActivationCode;
import com.vetspace.domain.access.Pack;
import com.vetspace.domain.access.Subscription;
import com.vetspace.domain.content.Course;
import com.vetspace.domain.content.Module;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class QuestionAdminIntegrationTest {

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
    private UserRepository userRepository;

    @Autowired
    private PackRepository packRepository;

    @Autowired
    private ActivationCodeRepository activationCodeRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private JwtService jwtService;

    private School school;
    private Course course;
    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        // No per-test rollback in this suite: several assertions count rows globally,
        // so questions/propositions must start from zero every time.
        propositionRepository.deleteAll();
        questionRepository.deleteAll();
        school = schoolRepository.save(School.builder().name("ENSV").slug("ensv-" + UUID.randomUUID()).build());
        Module module = moduleRepository.save(Module.builder()
            .school(school).studyYear(3).name("Anatomie").position(1).published(true).build());
        course = courseRepository.save(Course.builder()
            .module(module).name("Ostéologie").position(1).published(true).build());
        adminToken = tokenFor(Role.ADMIN);
        studentToken = tokenFor(Role.STUDENT);
    }

    private String tokenFor(Role role) {
        User user = userRepository.save(User.builder()
            .email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com")
            .username(role.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8))
            .passwordHash("$2a$12$0000000000000000000000000000000000000000000000000000")
            .lastName("T").firstName(role.name())
            .role(role).status(UserStatus.ACTIVE)
            .school(school)
            .studyYear(role == Role.STUDENT ? 3 : null)
            .emailVerified(true)
            .build());
        if (role == Role.STUDENT) {
            // This suite tests question mechanics, not the paywall — students get a matching subscription.
            Instant expires = Instant.now().plus(365, ChronoUnit.DAYS);
            Pack pack = packRepository.save(Pack.builder()
                .school(school).studyYear(3).name("Pack test").academicYear("2026-2027")
                .priceDa(1000).active(true).expiresAt(expires).build());
            ActivationCode code = activationCodeRepository.save(ActivationCode.builder()
                .pack(pack).codeHash("test-" + UUID.randomUUID()).maxUses(1).usedCount(1).revoked(false).build());
            subscriptionRepository.save(Subscription.builder()
                .user(user).pack(pack).activationCode(code)
                .startsAt(Instant.now().minusSeconds(60)).endsAt(expires).build());
        }
        return jwtService.generateAccessToken(user);
    }

    // ---------------------------------------------------------------
    // Proposition rules
    // ---------------------------------------------------------------

    @Test
    void createHappyPathSanitizesExplanationHtml() throws Exception {
        ObjectNode body = questionBody(course.getId(), "Statement A", true);
        ((ObjectNode) body.get("propositions").get(0))
            .put("explanationHtml", "<p>ok</p><script>alert(1)</script><span style=\"color: red; position: fixed\">warn</span>");

        MvcResult result = mockMvc.perform(post("/api/admin/questions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.propositions[0].letter").value("A"))
            .andReturn();

        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String explanation = created.get("propositions").get(0).get("explanationHtml").asText();
        assertThat(explanation).isEqualTo("<p>ok</p><span style=\"color: red\">warn</span>");
    }

    @Test
    void allTruePropositionsRejected() throws Exception {
        ObjectNode body = questionBody(course.getId(), "All true", true);
        body.get("propositions").forEach(p -> ((ObjectNode) p).put("isTrue", true));

        mockMvc.perform(post("/api/admin/questions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("false")));
        assertThat(questionRepository.count()).isZero();
    }

    @Test
    void singlePropositionRejected() throws Exception {
        ObjectNode body = questionBody(course.getId(), "Solo", true);
        ((ArrayNode) body.get("propositions")).remove(1);

        mockMvc.perform(post("/api/admin/questions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isBadRequest());
        assertThat(questionRepository.count()).isZero();
    }

    @Test
    void duplicateLettersRejected() throws Exception {
        ObjectNode body = questionBody(course.getId(), "Dup letters", true);
        ((ObjectNode) body.get("propositions").get(1)).put("letter", "A");

        mockMvc.perform(post("/api/admin/questions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------
    // Bulk import
    // ---------------------------------------------------------------

    @Test
    void importHappyPathPersistsAllRows() throws Exception {
        ArrayNode rows = objectMapper.createArrayNode();
        rows.add(questionBody(course.getId(), "Import 1", true));
        rows.add(questionBody(course.getId(), "Import 2", false));

        mockMvc.perform(post("/api/admin/questions/import")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(rows.toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.imported").value(2))
            .andExpect(jsonPath("$.questionIds.length()").value(2));

        assertThat(questionRepository.count()).isEqualTo(2);
        assertThat(propositionRepository.count()).isEqualTo(4);
    }

    @Test
    void importIsAllOrNothingWithPerRowErrors() throws Exception {
        ArrayNode rows = objectMapper.createArrayNode();
        rows.add(questionBody(course.getId(), "Good row", true));
        ObjectNode bad = questionBody(UUID.randomUUID(), "", true); // unknown course + blank statement
        bad.get("propositions").forEach(p -> ((ObjectNode) p).put("isTrue", true)); // and no false proposition
        rows.add(bad);

        MvcResult result = mockMvc.perform(post("/api/admin/questions/import")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(rows.toString()))
            .andExpect(status().isBadRequest())
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode errors = response.get("errors");
        assertThat(errors).isNotNull();
        assertThat(errors.size()).isGreaterThanOrEqualTo(3);
        errors.forEach(e -> {
            assertThat(e.get("row").asInt()).isEqualTo(1);
            assertThat(e.hasNonNull("field")).isTrue();
            assertThat(e.hasNonNull("message")).isTrue();
        });
        // Nothing persisted — not even the good row 0.
        assertThat(questionRepository.count()).isZero();
    }

    // ---------------------------------------------------------------
    // Filters
    // ---------------------------------------------------------------

    @Test
    void adminListFilters() throws Exception {
        Course otherCourse = courseRepository.save(Course.builder()
            .module(moduleRepository.findById(course.getModule().getId()).orElseThrow())
            .name("Autre").position(2).published(true).build());

        createQuestion(course.getId(), "Le fémur du chien", true);
        createQuestion(course.getId(), "Le crâne du chat", false);
        createQuestion(otherCourse.getId(), "Vertèbres cervicales", true);

        mockMvc.perform(get("/api/admin/questions?courseId=" + course.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/admin/questions?published=false")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].statement").value("Le crâne du chat"));

        mockMvc.perform(get("/api/admin/questions?q=fémur")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].statement").value("Le fémur du chien"));

        mockMvc.perform(get("/api/admin/questions?moduleId=" + course.getModule().getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3));
    }

    // ---------------------------------------------------------------
    // Student count
    // ---------------------------------------------------------------

    @Test
    void studentCountSeesPublishedOnly() throws Exception {
        createQuestion(course.getId(), "Published one", true);
        createQuestion(course.getId(), "Draft one", false);

        mockMvc.perform(get("/api/questions/count?courseIds=" + course.getId())
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(get("/api/questions/count?courseIds=" + course.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void studentCrudOnQuestionsForbidden() throws Exception {
        ObjectNode body = questionBody(course.getId(), "Nope", true);
        mockMvc.perform(post("/api/admin/questions")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------

    private void createQuestion(UUID courseId, String statement, boolean published) throws Exception {
        mockMvc.perform(post("/api/admin/questions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(questionBody(courseId, statement, published).toString()))
            .andExpect(status().isCreated());
    }

    private ObjectNode questionBody(UUID courseId, String statement, boolean published) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("courseId", courseId.toString());
        body.put("statement", statement);
        body.put("published", published);
        ArrayNode propositions = body.putArray("propositions");
        ObjectNode a = propositions.addObject();
        a.put("letter", "A");
        a.put("text", "Vrai choix");
        a.put("isTrue", true);
        ObjectNode b = propositions.addObject();
        b.put("letter", "B");
        b.put("text", "Faux choix");
        b.put("isTrue", false);
        return body;
    }
}
