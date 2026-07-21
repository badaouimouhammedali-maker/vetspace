package com.vetspace.session;

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
import com.vetspace.repository.PackRepository;
import com.vetspace.repository.SubscriptionRepository;
import com.vetspace.repository.ModuleRepository;
import com.vetspace.repository.PropositionRepository;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.SessionAnswerRepository;
import com.vetspace.repository.SessionQuestionRepository;
import com.vetspace.repository.SessionRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
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
class SessionIntegrationTest {

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
    private SessionRepository sessionRepository;

    @Autowired
    private SessionQuestionRepository sessionQuestionRepository;

    @Autowired
    private SessionAnswerRepository sessionAnswerRepository;

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
    private String studentToken;
    private String otherStudentToken;
    // q1: A,C true. q2: A true. q3: B true.
    private Question q1;
    private Question q2;
    private Question q3;

    @BeforeEach
    void setUp() {
        sessionAnswerRepository.deleteAll();
        sessionQuestionRepository.deleteAll();
        sessionRepository.deleteAll();
        propositionRepository.deleteAll();
        questionRepository.deleteAll();

        school = schoolRepository.save(School.builder().name("ENSV").slug("ensv-" + UUID.randomUUID()).build());
        Module module = moduleRepository.save(Module.builder()
            .school(school).studyYear(3).name("Anatomie").position(1).published(true).build());
        course = courseRepository.save(Course.builder()
            .module(module).name("Ostéologie").position(1).published(true).build());

        q1 = question("Q1", true, Map.of("A", true, "B", false, "C", true));
        q2 = question("Q2", true, Map.of("A", true, "B", false));
        q3 = question("Q3", true, Map.of("A", false, "B", true));

        studentToken = tokenFor(Role.STUDENT, school);
        otherStudentToken = tokenFor(Role.STUDENT, school);
    }

    private Question question(String statement, boolean published, Map<String, Boolean> letters) {
        Question q = questionRepository.save(Question.builder()
            .course(course).statement(statement).published(published).build());
        int pos = 1;
        for (var entry : letters.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            propositionRepository.save(Proposition.builder()
                .question(q).letter(entry.getKey()).text("Prop " + entry.getKey())
                .isTrue(entry.getValue())
                .explanationHtml("<p>Explication " + entry.getKey() + "</p>")
                .position(pos++)
                .build());
        }
        return q;
    }

    private String tokenFor(Role role, School school) {
        User user = userRepository.save(User.builder()
            .email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com")
            .username("u" + UUID.randomUUID().toString().substring(0, 12))
            .passwordHash("$2a$12$0000000000000000000000000000000000000000000000000000")
            .lastName("T").firstName(role.name())
            .role(role).status(UserStatus.ACTIVE)
            .school(school).studyYear(3)
            .emailVerified(true)
            .build());
        if (role == Role.STUDENT) {
            subscribe(user, school);
        }
        return jwtService.generateAccessToken(user);
    }

    /** These suites test session mechanics, not the paywall — students get a matching active subscription. */
    private void subscribe(User user, School school) {
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

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private String createSession(int questionCount) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "sessionType", "ENTRAINEMENT",
            "filters", Map.of("courseIds", List.of(course.getId().toString())),
            "questionCount", questionCount));
        MvcResult result = mockMvc.perform(post("/api/sessions")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asText();
    }

    private List<String> truePropositionIds(Question q) {
        return propositionRepository.findByQuestionId(q.getId()).stream()
            .filter(Proposition::isTrue).map(p -> p.getId().toString()).toList();
    }

    private JsonNode answerQuestion(String sessionId, UUID questionId, List<String> selectedIds, int seconds) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "selectedPropositionIds", selectedIds, "secondsSpent", seconds));
        MvcResult result = mockMvc.perform(put("/api/sessions/" + sessionId + "/questions/" + questionId + "/answer")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    @Test
    void fullLifecycle() throws Exception {
        String sessionId = createSession(10);

        // 3 published questions exist -> session capped at 3; count endpoint agrees.
        mockMvc.perform(get("/api/questions/count?courseIds=" + course.getId())
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(jsonPath("$.count").value(3));
        MvcResult play = mockMvc.perform(get("/api/sessions/" + sessionId + "/play")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode playJson = objectMapper.readTree(play.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(playJson.get("questions")).hasSize(3);

        // Exact-set correct answer on q1 returns that question's correction only.
        JsonNode correction = answerQuestion(sessionId, q1.getId(), truePropositionIds(q1), 30);
        assertThat(correction.get("isCorrect").asBoolean()).isTrue();
        assertThat(correction.get("propositions").findValues("isTrue")).isNotEmpty();
        assertThat(correction.get("propositions").get(0).get("explanationHtml").asText()).contains("Explication");

        // Partial selection on q1 via re-answer (upsert): only one of the two true propositions -> wrong.
        JsonNode partial = answerQuestion(sessionId, q1.getId(), List.of(truePropositionIds(q1).get(0)), 5);
        assertThat(partial.get("isCorrect").asBoolean()).isFalse();

        // Correct q2, consult q3.
        answerQuestion(sessionId, q2.getId(), truePropositionIds(q2), 10);
        mockMvc.perform(post("/api/sessions/" + sessionId + "/questions/" + q3.getId() + "/consult")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("CONSULTED"))
            .andExpect(jsonPath("$.isCorrect").doesNotExist());

        // Submit: 1 correct of 3 -> 33.33.
        mockMvc.perform(post("/api/sessions/" + sessionId + "/submit")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.score").value(33.33))
            .andExpect(jsonPath("$.totalSeconds").value(45));

        // Answering after submit -> 409.
        mockMvc.perform(put("/api/sessions/" + sessionId + "/questions/" + q2.getId() + "/answer")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("selectedPropositionIds", List.of(), "secondsSpent", 0))))
            .andExpect(status().isConflict());

        // Patch favorite + rating + title.
        mockMvc.perform(patch("/api/sessions/" + sessionId)
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("favorite", true, "rating", 4, "title", "Ma session"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.favorite").value(true))
            .andExpect(jsonPath("$.rating").value(4))
            .andExpect(jsonPath("$.title").value("Ma session"));

        // List: shows progress and the session.
        mockMvc.perform(get("/api/sessions").header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].answeredCount").value(2))
            .andExpect(jsonPath("$.content[0].correctCount").value(1))
            .andExpect(jsonPath("$.content[0].favorite").value(true));
    }

    @Test
    void answerRejectsForeignQuestionAndNegativeTime() throws Exception {
        String sessionId = createSession(10);

        // A question id that is not part of this session reads as nonexistent -> 404.
        mockMvc.perform(put("/api/sessions/" + sessionId + "/questions/" + UUID.randomUUID() + "/answer")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("selectedPropositionIds", List.of(), "secondsSpent", 5))))
            .andExpect(status().isNotFound());

        // Negative time fails bean validation -> 400.
        mockMvc.perform(put("/api/sessions/" + sessionId + "/questions/" + q1.getId() + "/answer")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "selectedPropositionIds", truePropositionIds(q1), "secondsSpent", -5))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void autoTitleUsesModuleName() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "sessionType", "ENTRAINEMENT",
            "filters", Map.of("moduleIds", List.of(course.getModule().getId().toString())),
            "questionCount", 3));
        mockMvc.perform(post("/api/sessions")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value(org.hamcrest.Matchers.startsWith("Anatomie (")));
    }

    @Test
    void zeroMatchingQuestionsRejected() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "sessionType", "ENTRAINEMENT",
            "filters", Map.of("courseIds", List.of(UUID.randomUUID().toString())),
            "questionCount", 5));
        mockMvc.perform(post("/api/sessions")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------
    // Repeat modes
    // ---------------------------------------------------------------

    @Test
    void repeatModesBuildCorrectQuestionSets() throws Exception {
        String sessionId = createSession(10);
        answerQuestion(sessionId, q1.getId(), truePropositionIds(q1), 5);                        // correct
        answerQuestion(sessionId, q2.getId(), List.of(), 5);                                     // wrong (empty set)
        mockMvc.perform(post("/api/sessions/" + sessionId + "/questions/" + q3.getId() + "/consult")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk());                                                          // consulted, not answered

        assertThat(repeatAndGetQuestionIds(sessionId, "ALL")).containsExactlyInAnyOrder(
            q1.getId().toString(), q2.getId().toString(), q3.getId().toString());
        assertThat(repeatAndGetQuestionIds(sessionId, "WRONG_ONLY")).containsExactly(q2.getId().toString());
        assertThat(repeatAndGetQuestionIds(sessionId, "UNANSWERED_ONLY")).containsExactly(q3.getId().toString());
        assertThat(repeatAndGetQuestionIds(sessionId, "SAME_FILTERS")).hasSize(3);
    }

    private List<String> repeatAndGetQuestionIds(String sessionId, String mode) throws Exception {
        MvcResult repeat = mockMvc.perform(post("/api/sessions/" + sessionId + "/repeat")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("mode", mode))))
            .andExpect(status().isCreated())
            .andReturn();
        String newId = objectMapper.readTree(repeat.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asText();

        MvcResult play = mockMvc.perform(get("/api/sessions/" + newId + "/play")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode questions = objectMapper.readTree(play.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("questions");
        List<String> ids = new ArrayList<>();
        questions.forEach(q -> ids.add(q.get("question").get("id").asText()));
        // New session starts clean.
        questions.forEach(q -> assertThat(q.get("state").asText()).isEqualTo("UNANSWERED"));
        return ids;
    }

    // ---------------------------------------------------------------
    // Reset
    // ---------------------------------------------------------------

    @Test
    void resetZeroesEverything() throws Exception {
        String sessionId = createSession(10);
        answerQuestion(sessionId, q1.getId(), truePropositionIds(q1), 42);
        mockMvc.perform(post("/api/sessions/" + sessionId + "/submit")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk());

        MvcResult reset = mockMvc.perform(post("/api/sessions/" + sessionId + "/reset")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode json = objectMapper.readTree(reset.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(json.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(json.get("totalSeconds").asInt()).isZero();
        json.get("questions").forEach(q -> {
            assertThat(q.get("state").asText()).isEqualTo("UNANSWERED");
            assertThat(q.get("secondsSpent").asInt()).isZero();
            assertThat(q.get("selectedPropositionIds")).isEmpty();
        });
        assertThat(sessionAnswerRepository.count()).isZero();
    }

    // ---------------------------------------------------------------
    // Ownership
    // ---------------------------------------------------------------

    @Test
    void foreignSessionIs404ForEveryEndpoint() throws Exception {
        String sessionId = createSession(10);
        String other = "Bearer " + otherStudentToken;

        mockMvc.perform(get("/api/sessions/" + sessionId + "/play").header("Authorization", other))
            .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/sessions/" + sessionId + "/questions/" + q1.getId() + "/answer")
                .header("Authorization", other)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("selectedPropositionIds", List.of(), "secondsSpent", 0))))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/sessions/" + sessionId + "/questions/" + q1.getId() + "/consult").header("Authorization", other))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/sessions/" + sessionId + "/submit").header("Authorization", other))
            .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/sessions/" + sessionId).header("Authorization", other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":true}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/sessions/" + sessionId + "/repeat").header("Authorization", other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"ALL\"}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/sessions/" + sessionId + "/reset").header("Authorization", other))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/sessions/" + sessionId).header("Authorization", other))
            .andExpect(status().isNotFound());

        // Owner still deletes fine.
        mockMvc.perform(delete("/api/sessions/" + sessionId).header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------
    // Play DTO leak
    // ---------------------------------------------------------------

    @Test
    void playResponseNeverLeaksTruthOrExplanations() throws Exception {
        String sessionId = createSession(10);
        // Even after answering, the play payload must stay clean.
        answerQuestion(sessionId, q1.getId(), truePropositionIds(q1), 5);

        MvcResult play = mockMvc.perform(get("/api/sessions/" + sessionId + "/play")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode json = objectMapper.readTree(play.getResponse().getContentAsString(StandardCharsets.UTF_8));

        List<String> fieldNames = new ArrayList<>();
        collectFieldNames(json, fieldNames);
        for (String name : fieldNames) {
            String normalized = name.toLowerCase(Locale.ROOT);
            assertThat(normalized).doesNotContain("istrue");
            assertThat(normalized).doesNotContain("is_true");
            assertThat(normalized).doesNotContain("explanation");
        }
    }

    private void collectFieldNames(JsonNode node, List<String> names) {
        if (node.isObject()) {
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String name = fields.next();
                names.add(name);
                collectFieldNames(node.get(name), names);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collectFieldNames(child, names));
        }
    }

    // ---------------------------------------------------------------
    // Count consistency
    // ---------------------------------------------------------------

    @Test
    void countEndpointMatchesCreatedSessionSize() throws Exception {
        MvcResult count = mockMvc.perform(get("/api/questions/count?courseIds=" + course.getId())
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andReturn();
        long expected = objectMapper.readTree(count.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("count").asLong();

        String sessionId = createSession(999);
        mockMvc.perform(get("/api/sessions/" + sessionId + "/play")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(jsonPath("$.questions.length()").value(expected));
    }

    // ---------------------------------------------------------------
    // What a submitted session leaves behind
    // ---------------------------------------------------------------

    /**
     * A submitted session has to be readable after the fact.
     *
     * <p>The player renders its end screen from {@code /play} whenever the session is
     * SUBMITTED, but the score used to live only on the submit <em>response</em>. So the
     * screen showed the score once and then "—" forever after: on a refresh, or when the
     * student reopened a finished session from the list, over work that had been scored
     * and stored. This asserts the revisit path specifically — a test that only checked
     * the submit response passed throughout the bug.
     *
     * <p>The weekly buckets are asserted in the same test because they share a cause:
     * both are "did finishing a session actually record anything a student can see
     * later". Answering writes {@code answeredAt}, which is what the weekly aggregate
     * groups by.
     */
    @Test
    void submittedSessionExposesItsScoreOnRevisitAndFillsTodaysWeeklyBucket() throws Exception {
        String sessionId = createSession(3);

        // One right, one wrong, one consulted -> a score that is neither 0 nor 100, so a
        // hard-coded or defaulted value cannot pass by accident.
        answerQuestion(sessionId, q1.getId(), truePropositionIds(q1), 5);
        answerQuestion(sessionId, q2.getId(), List.of(), 5);
        mockMvc.perform(post("/api/sessions/" + sessionId + "/questions/" + q3.getId() + "/consult")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/sessions/" + sessionId + "/submit")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.score").value(33.33));

        // The revisit: a fresh GET, exactly what a refresh or a click from the session
        // list does. No submit response in play here.
        mockMvc.perform(get("/api/sessions/" + sessionId + "/play")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.score").exists())
            .andExpect(jsonPath("$.score").value(33.33));

        // Today's weekly bucket reflects the work: 1 right, 1 wrong, 1 consulted.
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        mockMvc.perform(get("/api/stats/weekly")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(7))
            .andExpect(jsonPath("$[6].date").value(today))
            .andExpect(jsonPath("$[6].juste").value(1))
            .andExpect(jsonPath("$[6].fausse").value(1))
            .andExpect(jsonPath("$[6].consultees").value(1));
    }

    /** While a session is still ACTIVE there is no score yet, and none is invented. */
    @Test
    void activeSessionReportsNoScore() throws Exception {
        String sessionId = createSession(3);
        mockMvc.perform(get("/api/sessions/" + sessionId + "/play")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.score").doesNotExist());
    }
}
