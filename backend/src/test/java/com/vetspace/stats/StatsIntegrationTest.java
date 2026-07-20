package com.vetspace.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.vetspace.repository.ModuleRepository;
import com.vetspace.repository.PackRepository;
import com.vetspace.repository.PropositionRepository;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.SessionAnswerRepository;
import com.vetspace.repository.SessionQuestionRepository;
import com.vetspace.repository.SessionRepository;
import com.vetspace.repository.SubscriptionRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
class StatsIntegrationTest {

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
    private SessionQuestionRepository sessionQuestionRepository;

    @Autowired
    private SessionAnswerRepository sessionAnswerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private School school;
    private Course course;
    private String studentToken;
    private Question q1;
    private Question q2;
    private Question q3;
    private Question q4;

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
        q1 = question("Q1");
        q2 = question("Q2");
        q3 = question("Q3");
        q4 = question("Q4");

        User student = userRepository.save(User.builder()
            .email("stats-" + UUID.randomUUID() + "@example.com")
            .username("u" + UUID.randomUUID().toString().substring(0, 12))
            .passwordHash("$2a$12$0000000000000000000000000000000000000000000000000000")
            .lastName("T").firstName("S")
            .role(Role.STUDENT).status(UserStatus.ACTIVE)
            .school(school).studyYear(3)
            .emailVerified(true)
            .build());
        subscribe(student);
        studentToken = jwtService.generateAccessToken(student);
    }

    private Question question(String statement) {
        Question q = questionRepository.save(Question.builder()
            .course(course).statement(statement).published(true).build());
        propositionRepository.save(Proposition.builder()
            .question(q).letter("A").text("Vrai").isTrue(true).position(1).build());
        propositionRepository.save(Proposition.builder()
            .question(q).letter("B").text("Faux").isTrue(false).position(2).build());
        return q;
    }

    private void subscribe(User user) {
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

    private String truePropositionId(Question q) {
        return propositionRepository.findByQuestionId(q.getId()).stream()
            .filter(Proposition::isTrue).findFirst().orElseThrow().getId().toString();
    }

    private String falsePropositionId(Question q) {
        return propositionRepository.findByQuestionId(q.getId()).stream()
            .filter(p -> !p.isTrue()).findFirst().orElseThrow().getId().toString();
    }

    /** Known fixture: 4 questions; q1,q2 answered correctly (10s+20s), q3 wrong (30s), q4 consulted. */
    private String seedAnsweredSession() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/sessions")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "sessionType", "ENTRAINEMENT",
                    "filters", Map.of("courseIds", List.of(course.getId().toString())),
                    "questionCount", 10))))
            .andExpect(status().isCreated())
            .andReturn();
        String sessionId = objectMapper.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .get("id").asText();

        answer(sessionId, q1, truePropositionId(q1), 10);
        answer(sessionId, q2, truePropositionId(q2), 20);
        answer(sessionId, q3, falsePropositionId(q3), 30);
        mockMvc.perform(post("/api/sessions/" + sessionId + "/questions/" + q4.getId() + "/consult")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk());
        return sessionId;
    }

    private void answer(String sessionId, Question q, String propositionId, int seconds) throws Exception {
        mockMvc.perform(put("/api/sessions/" + sessionId + "/questions/" + q.getId() + "/answer")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "selectedPropositionIds", List.of(propositionId), "secondsSpent", seconds))))
            .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // Session stats math
    // ---------------------------------------------------------------

    @Test
    void sessionStatsMatchKnownFixture() throws Exception {
        seedAnsweredSession();

        // 4 questions, 3 answered (2 juste, 1 fausse), 1 consulted, 60s total ->
        // avg 15s/question, precision 2/3 = 66.67%.
        mockMvc.perform(get("/api/stats/sessions").header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].totalQuestions").value(4))
            .andExpect(jsonPath("$[0].juste").value(2))
            .andExpect(jsonPath("$[0].fausse").value(1))
            .andExpect(jsonPath("$[0].consulte").value(1))
            .andExpect(jsonPath("$[0].totalSeconds").value(60))
            .andExpect(jsonPath("$[0].avgSecondsPerQuestion").value(15.0))
            .andExpect(jsonPath("$[0].precisionPercent").value(66.67));

        // Type filter: no EXAMEN sessions.
        mockMvc.perform(get("/api/stats/sessions?type=EXAMEN").header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void byCourseGroupsTheSameMetrics() throws Exception {
        String sessionId = seedAnsweredSession();

        mockMvc.perform(get("/api/stats/sessions/" + sessionId + "/by-course")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].courseName").value("Ostéologie"))
            .andExpect(jsonPath("$[0].totalQuestions").value(4))
            .andExpect(jsonPath("$[0].juste").value(2))
            .andExpect(jsonPath("$[0].fausse").value(1))
            .andExpect(jsonPath("$[0].consulte").value(1))
            .andExpect(jsonPath("$[0].totalSeconds").value(60))
            .andExpect(jsonPath("$[0].precisionPercent").value(66.67));
    }

    @Test
    void byCourseOfForeignSessionIs404() throws Exception {
        String sessionId = seedAnsweredSession();
        User other = userRepository.save(User.builder()
            .email("other-" + UUID.randomUUID() + "@example.com")
            .username("o" + UUID.randomUUID().toString().substring(0, 12))
            .passwordHash("$2a$12$0000000000000000000000000000000000000000000000000000")
            .lastName("T").firstName("O")
            .role(Role.STUDENT).status(UserStatus.ACTIVE)
            .school(school).studyYear(3)
            .emailVerified(true)
            .build());
        mockMvc.perform(get("/api/stats/sessions/" + sessionId + "/by-course")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(other)))
            .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------
    // Weekly buckets
    // ---------------------------------------------------------------

    @Test
    void weeklyBucketsTodayAndZeroFillsTheRest() throws Exception {
        seedAnsweredSession();

        MvcResult result = mockMvc.perform(get("/api/stats/weekly")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode days = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(days).hasSize(7);
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        JsonNode last = days.get(6);
        assertThat(last.get("date").asText()).isEqualTo(today);
        assertThat(last.get("juste").asLong()).isEqualTo(2);
        assertThat(last.get("fausse").asLong()).isEqualTo(1);
        assertThat(last.get("consultees").asLong()).isEqualTo(1);
        for (int i = 0; i < 6; i++) {
            assertThat(days.get(i).get("juste").asLong()).isZero();
            assertThat(days.get(i).get("fausse").asLong()).isZero();
            assertThat(days.get(i).get("consultees").asLong()).isZero();
        }
    }

    // ---------------------------------------------------------------
    // Overview
    // ---------------------------------------------------------------

    @Test
    void overviewShowsBankLastSessionAndSubscription() throws Exception {
        seedAnsweredSession();

        mockMvc.perform(get("/api/stats/overview").header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bank.questions").value(4))
            .andExpect(jsonPath("$.bank.mindmaps").value(0))
            .andExpect(jsonPath("$.lastSession.totalQuestions").value(4))
            .andExpect(jsonPath("$.lastSession.juste").value(2))
            .andExpect(jsonPath("$.activeSubscriptions.length()").value(1))
            .andExpect(jsonPath("$.activeSubscriptions[0].packName").value("Pack test"));
    }
}
