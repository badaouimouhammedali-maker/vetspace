package com.vetspace.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetspace.domain.access.Pack;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
class CodeRedemptionIntegrationTest {

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
        registry.add("app.demo.question-limit", () -> 10);
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

    @Autowired
    private RedeemRateLimiter redeemRateLimiter;

    private School school;
    private Course paidCourse;
    private Course freeCourse;
    private Pack pack;
    private String adminToken;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        redeemRateLimiter.resetForTests();
        sessionAnswerRepository.deleteAll();
        sessionQuestionRepository.deleteAll();
        sessionRepository.deleteAll();
        subscriptionRepository.deleteAll();
        activationCodeRepository.deleteAll();

        school = schoolRepository.save(School.builder().name("ENSV").slug("ensv-" + UUID.randomUUID()).build());
        Module module = moduleRepository.save(Module.builder()
            .studyYear(3).name(moduleName).position(1).published(true).build());
        paidCourse = courseRepository.save(Course.builder()
            .module(module).name("Payant").position(1).published(true).freePreview(false).build());
        freeCourse = courseRepository.save(Course.builder()
            .module(module).name("Gratuit").position(2).published(true).freePreview(true).build());
        seedQuestions(paidCourse, 3);
        seedQuestions(freeCourse, 3);

        pack = packRepository.save(Pack.builder()
            .studyYear(3).name("Pack 3A").academicYear(uniqueAcademicYear())
            .priceDa(3500).active(true)
            // Truncate to micros: Postgres timestamps hold microseconds, while Instant.now()
            // is nanosecond-precision on Linux (and micro on macOS). Without this the in-memory
            // value never equals the DB round-trip, so assertions pass locally but fail on CI.
            .expiresAt(Instant.now().plus(300, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS))
            .build());

        adminToken = tokenFor(newUser(Role.ADMIN));

        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(logAppender);
    }

    @AfterEach
    void tearDownLogCapture() {
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(logAppender);
    }

    private void seedQuestions(Course course, int count) {
        for (int i = 0; i < count; i++) {
            Question q = questionRepository.save(Question.builder()
                .course(course).statement(course.getName() + " Q" + i).published(true).build());
            propositionRepository.save(Proposition.builder()
                .question(q).letter("A").text("Vrai").isTrue(true).position(1).build());
            propositionRepository.save(Proposition.builder()
                .question(q).letter("B").text("Faux").isTrue(false).position(2).build());
        }
    }

    private User newUser(Role role) {
        return userRepository.save(User.builder()
            .email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com")
            .username("u" + UUID.randomUUID().toString().substring(0, 12))
            .passwordHash("$2a$12$0000000000000000000000000000000000000000000000000000")
            .lastName("T").firstName(role.name())
            .role(role).status(UserStatus.ACTIVE)
            .school(school).studyYear(role == Role.STUDENT ? 3 : null)
            .emailVerified(true)
            .build());
    }

    private String tokenFor(User user) {
        return jwtService.generateAccessToken(user);
    }

    private List<String> generateCodes(int count, Integer maxUses) throws Exception {
        Map<String, Object> body = maxUses == null
            ? Map.of("packId", pack.getId().toString(), "count", count)
            : Map.of("packId", pack.getId().toString(), "count", count, "maxUses", maxUses);
        MvcResult result = mockMvc.perform(post("/api/admin/codes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<String> codes = new ArrayList<>();
        json.get("codes").forEach(c -> codes.add(c.asText()));
        return codes;
    }

    private MvcResult redeem(String token, String code, String ip) throws Exception {
        return mockMvc.perform(post("/api/codes/redeem")
                .header("Authorization", "Bearer " + token)
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", code))))
            .andReturn();
    }

    private String createSessionBody(UUID courseId, int count) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
            "sessionType", "ENTRAINEMENT",
            "filters", Map.of("courseIds", List.of(courseId.toString())),
            "questionCount", count));
    }

    // ---------------------------------------------------------------
    // Generate -> redeem -> gate opens
    // ---------------------------------------------------------------

    @Test
    void generateRedeemOpensTheGate() throws Exception {
        User student = newUser(Role.STUDENT);
        String studentToken = tokenFor(student);

        // Gate closed: count is 403 SUBSCRIPTION_REQUIRED, paid-course session too (demo can't reach paid content).
        mockMvc.perform(get("/api/questions/count?courseIds=" + paidCourse.getId())
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("SUBSCRIPTION_REQUIRED"));
        mockMvc.perform(post("/api/sessions")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON).content(createSessionBody(paidCourse.getId(), 5)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("SUBSCRIPTION_REQUIRED"));
        mockMvc.perform(get("/api/mindmaps?courseId=" + paidCourse.getId())
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("SUBSCRIPTION_REQUIRED"));

        // Codes are 16 chars of the unambiguous alphabet in XXXX-XXXX-XXXX-XXXX; only hashes hit the DB.
        List<String> codes = generateCodes(1, null);
        String code = codes.get(0);
        assertThat(code).matches("[A-HJ-KM-NP-Z2-9]{4}-[A-HJ-KM-NP-Z2-9]{4}-[A-HJ-KM-NP-Z2-9]{4}-[A-HJ-KM-NP-Z2-9]{4}");
        assertThat(activationCodeRepository.findAll())
            .noneMatch(c -> c.getCodeHash().contains(code.replace("-", "")));

        redeem(studentToken, code, "10.0.0.1").getResponse();
        assertThat(subscriptionRepository.findActiveForUser(student.getId(), Instant.now())).hasSize(1);
        assertThat(subscriptionRepository.findActiveForUser(student.getId(), Instant.now()).get(0).getEndsAt())
            .isEqualTo(pack.getExpiresAt());

        // Gate open.
        mockMvc.perform(get("/api/questions/count?courseIds=" + paidCourse.getId())
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(3));
        mockMvc.perform(post("/api/sessions")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON).content(createSessionBody(paidCourse.getId(), 5)))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/api/mindmaps?courseId=" + paidCourse.getId())
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // Single-use / double redeem
    // ---------------------------------------------------------------

    @Test
    void doubleRedeemOfSingleUseCodeFails() throws Exception {
        String code = generateCodes(1, 1).get(0);
        User s1 = newUser(Role.STUDENT);
        User s2 = newUser(Role.STUDENT);

        assertThat(redeem(tokenFor(s1), code, "10.0.1.1").getResponse().getStatus()).isEqualTo(200);
        MvcResult second = redeem(tokenFor(s2), code, "10.0.1.2");
        assertThat(second.getResponse().getStatus()).isEqualTo(400);
        assertThat(second.getResponse().getContentAsString()).contains("Invalid activation code");
        assertThat(subscriptionRepository.count()).isEqualTo(1);
    }

    // ---------------------------------------------------------------
    // Concurrency: exactly one winner
    // ---------------------------------------------------------------

    @Test
    void concurrentRedemptionOfSingleUseCodeHasExactlyOneWinner() throws Exception {
        String code = generateCodes(1, 1).get(0);
        int threads = 8;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tokens.add(tokenFor(newUser(Role.STUDENT)));
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await(10, TimeUnit.SECONDS);
                return redeem(tokens.get(idx), code, "10.0.2." + idx).getResponse().getStatus();
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 400).count()).isEqualTo(threads - 1);
        assertThat(subscriptionRepository.count()).isEqualTo(1);
        assertThat(activationCodeRepository.findAll().get(0).getUsedCount()).isEqualTo(1);
    }

    // ---------------------------------------------------------------
    // Revoked / expired: generic failure
    // ---------------------------------------------------------------

    @Test
    void revokedAndExpiredFailWithTheSameGenericMessage() throws Exception {
        String revokedCode = generateCodes(1, null).get(0);
        UUID codeId = activationCodeRepository.findAll().get(0).getId();
        mockMvc.perform(post("/api/admin/codes/" + codeId + "/revoke")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVOKED"));

        User student = newUser(Role.STUDENT);
        String token = tokenFor(student);
        MvcResult revoked = redeem(token, revokedCode, "10.0.3.1");
        MvcResult unknown = redeem(token, "AAAA-BBBB-CCCC-DDDD", "10.0.3.1");

        // Expired pack.
        pack.setExpiresAt(Instant.now().minusSeconds(60));
        packRepository.save(pack);
        String expiredCode = generateCodes(1, null).get(0);
        MvcResult expired = redeem(token, expiredCode, "10.0.3.1");
        pack.setExpiresAt(Instant.now().plus(300, ChronoUnit.DAYS));
        packRepository.save(pack);

        for (MvcResult r : List.of(revoked, unknown, expired)) {
            assertThat(r.getResponse().getStatus()).isEqualTo(400);
            JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
            assertThat(body.get("message").asText()).isEqualTo("Invalid activation code");
        }
    }

    // ---------------------------------------------------------------
    // Rate limit
    // ---------------------------------------------------------------

    @Test
    void redeemIsRateLimitedFivePerHour() throws Exception {
        User student = newUser(Role.STUDENT);
        String token = tokenFor(student);
        for (int i = 0; i < 5; i++) {
            assertThat(redeem(token, "XXXX-YYYY-ZZZZ-" + i + i + i + i, "10.0.4.1").getResponse().getStatus())
                .isEqualTo(400);
        }
        assertThat(redeem(token, "XXXX-YYYY-ZZZZ-9999", "10.0.4.1").getResponse().getStatus()).isEqualTo(429);
        // Also blocked from a fresh IP: the per-user bucket is already drained.
        assertThat(redeem(token, "XXXX-YYYY-ZZZZ-8888", "10.0.4.99").getResponse().getStatus()).isEqualTo(429);
    }

    // ---------------------------------------------------------------
    // Demo mode
    // ---------------------------------------------------------------

    @Test
    void demoModeAllowsExactlyOneFreePreviewSession() throws Exception {
        User student = newUser(Role.STUDENT);
        String token = tokenFor(student);

        // One demo session from the free-preview course, capped at the demo limit.
        MvcResult created = mockMvc.perform(post("/api/sessions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(createSessionBody(freeCourse.getId(), 50)))
            .andExpect(status().isCreated())
            .andReturn();
        JsonNode session = objectMapper.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(session.get("questionCount").asInt()).isLessThanOrEqualTo(10);
        String sessionId = session.get("id").asText();

        // The demo session is playable without a subscription.
        mockMvc.perform(get("/api/sessions/" + sessionId + "/play")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        // But only once: a second session (even free-preview) is refused.
        mockMvc.perform(post("/api/sessions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(createSessionBody(freeCourse.getId(), 5)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("SUBSCRIPTION_REQUIRED"));
    }

    @Test
    void demoModeCannotTouchPaidContent() throws Exception {
        User student = newUser(Role.STUDENT);
        String token = tokenFor(student);
        mockMvc.perform(post("/api/sessions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(createSessionBody(paidCourse.getId(), 5)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("SUBSCRIPTION_REQUIRED"));
        assertThat(sessionRepository.countByUserId(student.getId())).isZero();
    }

    // ---------------------------------------------------------------
    // CSV one-shot + codes never logged
    // ---------------------------------------------------------------

    @Test
    void csvIsOneShotAndPlaintextCodesNeverAppearInLogs() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/codes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("packId", pack.getId().toString(), "count", 3))))
            .andExpect(status().isCreated())
            .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<String> codes = new ArrayList<>();
        json.get("codes").forEach(c -> codes.add(c.asText()));
        String csvToken = json.get("csvToken").asText();

        // First download works and contains every code; second finds nothing.
        MvcResult csv = mockMvc.perform(get("/api/admin/codes/csv/" + csvToken)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        String csvBody = csv.getResponse().getContentAsString(StandardCharsets.UTF_8);
        codes.forEach(c -> assertThat(csvBody).contains(c));
        mockMvc.perform(get("/api/admin/codes/csv/" + csvToken)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound());

        // Redeem one to exercise the whole path, then assert no plaintext ever hit the logs.
        User student = newUser(Role.STUDENT);
        assertThat(redeem(tokenFor(student), codes.get(0), "10.0.5.1").getResponse().getStatus()).isEqualTo(200);

        List<String> logLines = logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        for (String code : codes) {
            String bare = code.replace("-", "");
            assertThat(logLines).noneMatch(line -> line.contains(code) || line.contains(bare));
        }
    }
}
