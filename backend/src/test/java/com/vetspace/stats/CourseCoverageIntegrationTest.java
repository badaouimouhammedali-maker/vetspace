package com.vetspace.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/**
 * GET /api/stats/courses — lifetime per-course coverage.
 *
 * <p>Assertions look courses up by id rather than by list position. The catalogue query
 * is global to a study year and {@code setUp} does not delete modules or courses, so
 * sibling test methods leave published year-3 courses behind that legitimately appear in
 * the response. Anything asserting on list length here would pass alone and fail in a
 * suite, which is how the e2e specs went order-dependent once already.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class CourseCoverageIntegrationTest {

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
    @Autowired private SessionRepository sessionRepository;
    @Autowired private SessionQuestionRepository sessionQuestionRepository;
    @Autowired private SessionAnswerRepository sessionAnswerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;

    /** Modules are unique on (study_year, name) nationally, so this must differ per method. */
    private final String moduleName = "Anatomie " + UUID.randomUUID();

    private School school;
    private Module module;
    private Course osteologie;
    private Course arthrologie;
    private Course myologie;
    private Course brouillon;
    private Question q1;
    private Question q2;
    private Question q3;
    private Question q4;
    private User student;
    private String studentToken;

    @BeforeEach
    void setUp() {
        sessionAnswerRepository.deleteAll();
        sessionQuestionRepository.deleteAll();
        sessionRepository.deleteAll();
        propositionRepository.deleteAll();
        questionRepository.deleteAll();

        school = schoolRepository.save(School.builder()
            .name("ENSV").slug("ensv-" + UUID.randomUUID()).build());
        module = moduleRepository.save(Module.builder()
            .studyYear(3).name(moduleName).position(1).published(true).build());

        osteologie = course("Ostéologie", 1, true);
        arthrologie = course("Arthrologie", 2, true);
        // No questions at all: the divide-by-zero case, and the one a coverage screen
        // most needs to keep showing rather than silently drop.
        myologie = course("Myologie", 3, true);
        // Unpublished: must never reach a student.
        brouillon = course("Brouillon", 4, false);

        q1 = question(osteologie, "Q1");
        q2 = question(osteologie, "Q2");
        q3 = question(osteologie, "Q3");
        q4 = question(osteologie, "Q4");
        question(arthrologie, "A1");
        question(arthrologie, "A2");
        question(arthrologie, "A3");
        question(brouillon, "B1");

        student = user("student");
        subscribe(student);
        studentToken = jwtService.generateAccessToken(student);
    }

    // ---------------------------------------------------------------
    // Fixture helpers
    // ---------------------------------------------------------------

    private Course course(String name, int position, boolean published) {
        return courseRepository.save(Course.builder()
            .module(module).name(name).position(position).published(published).build());
    }

    private Question question(Course course, String statement) {
        Question q = questionRepository.save(Question.builder()
            .course(course).statement(statement).published(true).build());
        propositionRepository.save(Proposition.builder()
            .question(q).letter("A").text("Vrai").isTrue(true).position(1).build());
        propositionRepository.save(Proposition.builder()
            .question(q).letter("B").text("Faux").isTrue(false).position(2).build());
        return q;
    }

    private User user(String prefix) {
        return userRepository.save(User.builder()
            .email(prefix + "-" + UUID.randomUUID() + "@example.com")
            .username("u" + UUID.randomUUID().toString().substring(0, 12))
            .passwordHash("$2a$12$0000000000000000000000000000000000000000000000000000")
            .lastName("T").firstName("S")
            .role(Role.STUDENT).status(UserStatus.ACTIVE)
            .school(school).studyYear(3)
            .emailVerified(true)
            .build());
    }

    private void subscribe(User user) {
        Instant expires = Instant.now().plus(365, ChronoUnit.DAYS);
        Pack pack = packRepository.save(Pack.builder()
            .studyYear(3).name("Pack test").academicYear("26/" + UUID.randomUUID().toString().substring(0, 8))
            .priceDa(1000).active(true).expiresAt(expires).build());
        ActivationCode code = activationCodeRepository.save(ActivationCode.builder()
            .pack(pack).codeHash("test-" + UUID.randomUUID()).maxUses(1).usedCount(1).revoked(false).build());
        subscriptionRepository.save(Subscription.builder()
            .user(user).pack(pack).activationCode(code)
            .startsAt(Instant.now().minusSeconds(60)).endsAt(expires).build());
    }

    private String propositionId(Question q, boolean correct) {
        return propositionRepository.findByQuestionId(q.getId()).stream()
            .filter(p -> p.isTrue() == correct).findFirst().orElseThrow().getId().toString();
    }

    /** A session over every question of one course, so its contents are known exactly. */
    private String startSession(String token, Course course) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/sessions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "sessionType", "ENTRAINEMENT",
                    "filters", Map.of("courseIds", List.of(course.getId().toString())),
                    "questionCount", 50))))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .get("id").asText();
    }

    private void answer(String token, String sessionId, Question q, boolean correct) throws Exception {
        mockMvc.perform(put("/api/sessions/" + sessionId + "/questions/" + q.getId() + "/answer")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "selectedPropositionIds", List.of(propositionId(q, correct)), "secondsSpent", 10))))
            .andExpect(status().isOk());
    }

    private void consult(String token, String sessionId, Question q) throws Exception {
        mockMvc.perform(post("/api/sessions/" + sessionId + "/questions/" + q.getId() + "/consult")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    private JsonNode coverage(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/stats/courses")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** The one course under test, found by id — see the class comment on why not by index. */
    private JsonNode courseNode(JsonNode modules, Course course) {
        for (JsonNode m : modules) {
            for (JsonNode c : m.get("courses")) {
                if (c.get("courseId").asText().equals(course.getId().toString())) {
                    return c;
                }
            }
        }
        throw new AssertionError("Course " + course.getName() + " missing from coverage response");
    }

    private JsonNode moduleNode(JsonNode modules, Module module) {
        for (JsonNode m : modules) {
            if (m.get("moduleId").asText().equals(module.getId().toString())) {
                return m;
            }
        }
        throw new AssertionError("Module " + module.getName() + " missing from coverage response");
    }

    private boolean containsCourse(JsonNode modules, Course course) {
        for (JsonNode m : modules) {
            for (JsonNode c : m.get("courses")) {
                if (c.get("courseId").asText().equals(course.getId().toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------
    // Coverage maths
    // ---------------------------------------------------------------

    @Test
    void coverageMatchesKnownFixture() throws Exception {
        // Ostéologie: q1,q2 right, q3 wrong, q4 consulted (seen, never answered).
        String session = startSession(studentToken, osteologie);
        answer(studentToken, session, q1, true);
        answer(studentToken, session, q2, true);
        answer(studentToken, session, q3, false);
        consult(studentToken, session, q4);

        JsonNode modules = coverage(studentToken);
        JsonNode osteo = courseNode(modules, osteologie);

        assertThat(osteo.get("courseName").asText()).isEqualTo("Ostéologie");
        assertThat(osteo.get("totalQuestions").asLong()).isEqualTo(4);
        // q4 counts as seen even though it was only consulted — same definition the
        // builder's "only unseen" filter uses, so the two screens cannot disagree.
        assertThat(osteo.get("seenQuestions").asLong()).isEqualTo(4);
        assertThat(osteo.get("answeredQuestions").asLong()).isEqualTo(3);
        assertThat(osteo.get("correctQuestions").asLong()).isEqualTo(2);
        assertThat(osteo.get("neverSeenQuestions").asLong()).isZero();
        // 2 correct / 3 answered — the consulted one is not in the denominator.
        assertThat(osteo.get("precisionPercent").asDouble()).isEqualTo(66.67);

        // A published course never touched: all three questions still to do.
        JsonNode arthro = courseNode(modules, arthrologie);
        assertThat(arthro.get("totalQuestions").asLong()).isEqualTo(3);
        assertThat(arthro.get("seenQuestions").asLong()).isZero();
        assertThat(arthro.get("neverSeenQuestions").asLong()).isEqualTo(3);
        assertThat(arthro.get("precisionPercent").asDouble()).isZero();

        // Module rollup is the sum of its courses.
        JsonNode moduleNode = moduleNode(modules, module);
        assertThat(moduleNode.get("totalQuestions").asLong()).isEqualTo(7);
        assertThat(moduleNode.get("seenQuestions").asLong()).isEqualTo(4);
    }

    @Test
    void correctInAnyLaterSessionCountsTheQuestionAsCorrect() throws Exception {
        String first = startSession(studentToken, osteologie);
        answer(studentToken, first, q1, true);
        answer(studentToken, first, q2, true);
        answer(studentToken, first, q3, false);
        consult(studentToken, first, q4);

        // Same four questions again; this time q3 is right. Counting is per distinct
        // question, so the second attempt corrects the first rather than adding to it.
        String second = startSession(studentToken, osteologie);
        answer(studentToken, second, q3, true);

        JsonNode osteo = courseNode(coverage(studentToken), osteologie);
        assertThat(osteo.get("seenQuestions").asLong()).isEqualTo(4);
        assertThat(osteo.get("answeredQuestions").asLong()).isEqualTo(3);
        assertThat(osteo.get("correctQuestions").asLong()).isEqualTo(3);
        assertThat(osteo.get("precisionPercent").asDouble()).isEqualTo(100.0);
    }

    // ---------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------

    @Test
    void courseWithNoQuestionsReportsZerosInsteadOfDividingByZero() throws Exception {
        JsonNode myo = courseNode(coverage(studentToken), myologie);

        assertThat(myo.get("totalQuestions").asLong()).isZero();
        assertThat(myo.get("seenQuestions").asLong()).isZero();
        assertThat(myo.get("answeredQuestions").asLong()).isZero();
        assertThat(myo.get("neverSeenQuestions").asLong()).isZero();
        // Not NaN, not Infinity, not a 500 — 0/0 is reported as 0%.
        assertThat(myo.get("precisionPercent").asDouble()).isZero();
    }

    @Test
    void unpublishedCourseIsInvisibleToStudents() throws Exception {
        assertThat(containsCourse(coverage(studentToken), brouillon)).isFalse();
    }

    @Test
    void unpublishingASeenQuestionNeverPushesProgressPastTheTotal() throws Exception {
        String session = startSession(studentToken, osteologie);
        answer(studentToken, session, q1, true);
        answer(studentToken, session, q2, true);
        answer(studentToken, session, q3, true);
        answer(studentToken, session, q4, true);

        // Retiring content the student has already worked through. If "seen" were counted
        // from session history alone it would stay at 4 against a total of 3, and the
        // progress bar would read 133%.
        q4.setPublished(false);
        questionRepository.save(q4);

        JsonNode osteo = courseNode(coverage(studentToken), osteologie);
        assertThat(osteo.get("totalQuestions").asLong()).isEqualTo(3);
        assertThat(osteo.get("seenQuestions").asLong()).isEqualTo(3);
        assertThat(osteo.get("seenQuestions").asLong())
            .isLessThanOrEqualTo(osteo.get("totalQuestions").asLong());
        assertThat(osteo.get("neverSeenQuestions").asLong()).isZero();
    }

    // ---------------------------------------------------------------
    // Isolation
    // ---------------------------------------------------------------

    @Test
    void anotherStudentsWorkNeverCountsTowardsMine() throws Exception {
        User other = user("other");
        subscribe(other);
        String otherToken = jwtService.generateAccessToken(other);

        // The other student works through Arthrologie completely; I have done nothing.
        String otherSession = startSession(otherToken, arthrologie);
        for (Question q : questionRepository.findAll()) {
            if (q.getCourse().getId().equals(arthrologie.getId())) {
                answer(otherToken, otherSession, q, true);
            }
        }

        JsonNode mine = courseNode(coverage(studentToken), arthrologie);
        assertThat(mine.get("seenQuestions").asLong()).isZero();
        assertThat(mine.get("answeredQuestions").asLong()).isZero();
        assertThat(mine.get("correctQuestions").asLong()).isZero();
        assertThat(mine.get("neverSeenQuestions").asLong()).isEqualTo(3);
        assertThat(mine.get("precisionPercent").asDouble()).isZero();

        // ...and theirs is complete, proving the fixture actually did the work above.
        JsonNode theirs = courseNode(coverage(otherToken), arthrologie);
        assertThat(theirs.get("correctQuestions").asLong()).isEqualTo(3);
        assertThat(theirs.get("precisionPercent").asDouble()).isEqualTo(100.0);
    }

    @Test
    void studentWithoutSubscriptionIsRefused() throws Exception {
        User broke = user("broke");
        mockMvc.perform(get("/api/stats/courses")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(broke)))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRefused() throws Exception {
        mockMvc.perform(get("/api/stats/courses")).andExpect(status().isUnauthorized());
    }
}
