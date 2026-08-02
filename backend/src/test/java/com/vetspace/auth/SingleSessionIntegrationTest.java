package com.vetspace.auth;

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
import com.vetspace.domain.user.RefreshToken;
import com.vetspace.domain.user.RevocationReason;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.repository.ActivationCodeRepository;
import com.vetspace.repository.CourseRepository;
import com.vetspace.repository.ModuleRepository;
import com.vetspace.repository.PackRepository;
import com.vetspace.repository.PropositionRepository;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.RefreshTokenRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.SessionAnswerRepository;
import com.vetspace.repository.SessionQuestionRepository;
import com.vetspace.repository.SessionRepository;
import com.vetspace.repository.SubscriptionRepository;
import com.vetspace.repository.UserRepository;
import jakarta.servlet.http.Cookie;
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
 * One active session per account.
 *
 * <p>Everything here goes through the HTTP surface with real cookies rather than calling
 * the service directly, because the thing being asserted is what a second device actually
 * experiences — including that the response carries a code the SPA can route on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class SingleSessionIntegrationTest {

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
        // Repeated logins in one test method otherwise trip the login rate limiter.
        registry.add("app.rate-limits-enabled", () -> false);
    }

    private static final String PASSWORD = "MotDePasse!2026";
    private static final String CHROME =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36";
    private static final String SAFARI_IPHONE =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
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
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private final String moduleName = "Anatomie " + UUID.randomUUID();

    private User student;
    private String email;
    private Course course;

    @BeforeEach
    void setUp() {
        sessionAnswerRepository.deleteAll();
        sessionQuestionRepository.deleteAll();
        sessionRepository.deleteAll();
        propositionRepository.deleteAll();
        questionRepository.deleteAll();

        School school = schoolRepository.save(School.builder()
            .name("ENSV").slug("ensv-" + UUID.randomUUID()).build());
        email = "session-" + UUID.randomUUID() + "@example.com";
        student = userRepository.save(User.builder()
            .email(email)
            .username("u" + UUID.randomUUID().toString().substring(0, 12))
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .lastName("T").firstName("S")
            .role(Role.STUDENT).status(UserStatus.ACTIVE)
            .school(school).studyYear(3)
            .emailVerified(true)
            .build());
        subscribe(student);

        Module module = moduleRepository.save(Module.builder()
            .studyYear(3).name(moduleName).position(1).published(true).build());
        course = courseRepository.save(Course.builder()
            .module(module).name("Ostéologie").position(1).published(true).build());
        for (int i = 1; i <= 4; i++) {
            question("Q" + i);
        }
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

    private Question question(String statement) {
        Question q = questionRepository.save(Question.builder()
            .course(course).statement(statement).published(true).build());
        propositionRepository.save(Proposition.builder()
            .question(q).letter("A").text("Vrai").isTrue(true).position(1).build());
        propositionRepository.save(Proposition.builder()
            .question(q).letter("B").text("Faux").isTrue(false).position(2).build());
        return q;
    }

    // ---------------------------------------------------------------
    // Helpers: a "device" is an access token plus its refresh cookie
    // ---------------------------------------------------------------

    private record Device(String accessToken, Cookie refreshCookie) {
    }

    private Device login(String userAgent) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .header("User-Agent", userAgent)
                .header("X-Forwarded-For", userAgent.equals(CHROME) ? "203.0.113.10" : "198.51.100.20")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .get("accessToken").asText();
        Cookie cookie = result.getResponse().getCookie(RefreshTokenService.COOKIE_NAME);
        assertThat(cookie).isNotNull();
        return new Device(token, cookie);
    }

    private MvcResult refresh(Device device) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(device.refreshCookie())).andReturn();
    }

    private long liveFamilies() {
        return refreshTokenRepository.findLiveFamilies(student.getId()).size();
    }

    // ---------------------------------------------------------------
    // The policy
    // ---------------------------------------------------------------

    @Test
    void secondLoginRevokesTheFirstFamily() throws Exception {
        login(CHROME);
        assertThat(liveFamilies()).isEqualTo(1);

        login(SAFARI_IPHONE);

        // Not two sessions coexisting — the first was closed, not merely outranked.
        assertThat(liveFamilies()).isEqualTo(1);
        List<RefreshToken> all = refreshTokenRepository.findAll().stream()
            .filter(t -> t.getUser().getId().equals(student.getId())).toList();
        assertThat(all).hasSize(2);
        assertThat(all).anyMatch(t -> t.getRevokedReason() == RevocationReason.SUPERSEDED);
    }

    @Test
    void theEvictedDevicesRefreshReturns401SessionSuperseded() throws Exception {
        Device first = login(CHROME);
        login(SAFARI_IPHONE);

        mockMvc.perform(post("/api/auth/refresh").cookie(first.refreshCookie()))
            .andExpect(status().isUnauthorized())
            // The code, not the message, is what the SPA routes on to show the specific
            // screen instead of the generic "session expirée".
            .andExpect(jsonPath("$.error").value("SESSION_SUPERSEDED"));
    }

    @Test
    void theEvictedUserCanSimplyLogInAgain() throws Exception {
        Device first = login(CHROME);
        login(SAFARI_IPHONE);
        mockMvc.perform(post("/api/auth/refresh").cookie(first.refreshCookie()))
            .andExpect(status().isUnauthorized());

        // The whole point of eviction over lockout: coming back is one login away.
        Device again = login(CHROME);
        assertThat(refresh(again).getResponse().getStatus()).isEqualTo(200);
        assertThat(liveFamilies()).isEqualTo(1);
    }

    @Test
    void theSurvivingDeviceKeepsWorking() throws Exception {
        login(CHROME);
        Device second = login(SAFARI_IPHONE);

        assertThat(refresh(second).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void aRotatedTokenReplayedIsStillTreatedAsReuseNotSupersession() throws Exception {
        Device device = login(CHROME);
        // Rotate once; the original token is now spent.
        assertThat(refresh(device).getResponse().getStatus()).isEqualTo(200);

        // Replaying the spent token must burn the family — this is the theft signal, and
        // the new SUPERSEDED path must not have swallowed it.
        mockMvc.perform(post("/api/auth/refresh").cookie(device.refreshCookie()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Unauthorized"));
        assertThat(liveFamilies()).isZero();
    }

    @Test
    void deviceLabelAndIpAreRecordedFromTheRequest() throws Exception {
        login(CHROME);

        RefreshToken token = refreshTokenRepository.findMostRecentLive(student.getId()).orElseThrow();
        assertThat(token.getDeviceLabel()).isEqualTo("Chrome sur Windows");
        assertThat(token.getCreatedIp()).isEqualTo("203.0.113.10");
        assertThat(token.getLastUsedAt()).isNotNull();
    }

    @Test
    void rotationTouchesLastUsedAt() throws Exception {
        Device device = login(CHROME);
        Instant atLogin = refreshTokenRepository.findMostRecentLive(student.getId()).orElseThrow().getLastUsedAt();

        Thread.sleep(20);
        assertThat(refresh(device).getResponse().getStatus()).isEqualTo(200);

        Instant afterRotation = refreshTokenRepository.findMostRecentLive(student.getId()).orElseThrow().getLastUsedAt();
        assertThat(afterRotation).isAfter(atLogin);
    }

    @Test
    void passwordChangeStillRevokesEverything() throws Exception {
        Device device = login(CHROME);

        mockMvc.perform(post("/api/users/me/password")
                .header("Authorization", "Bearer " + device.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("currentPassword", PASSWORD, "newPassword", "NouveauMotDePasse!2026"))))
            .andExpect(status().isNoContent());

        assertThat(liveFamilies()).isZero();
        // An ordinary expiry, not SESSION_SUPERSEDED: the user did this to themselves and
        // telling them another device took over would be a lie.
        mockMvc.perform(post("/api/auth/refresh").cookie(device.refreshCookie()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // ---------------------------------------------------------------
    // In-session safety (item 5): nothing answered is ever lost
    // ---------------------------------------------------------------

    @Test
    void answersSurviveEvictionMidQuizAndComeBackOnResume() throws Exception {
        Device first = login(CHROME);

        MvcResult created = mockMvc.perform(post("/api/sessions")
                .header("Authorization", "Bearer " + first.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "sessionType", "ENTRAINEMENT",
                    "filters", Map.of("courseIds", List.of(course.getId().toString())),
                    "questionCount", 10))))
            .andExpect(status().isCreated())
            .andReturn();
        String sessionId = objectMapper.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .get("id").asText();

        // Answer two of the four, correctly, then get evicted mid-quiz.
        List<Question> questions = questionRepository.findAll();
        answer(first, sessionId, questions.get(0));
        answer(first, sessionId, questions.get(1));

        login(SAFARI_IPHONE);
        mockMvc.perform(post("/api/auth/refresh").cookie(first.refreshCookie()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("SESSION_SUPERSEDED"));

        // Answers persist per question as they are given, so eviction cannot roll any of
        // them back — there is no end-of-quiz "save" to miss.
        assertThat(sessionAnswerRepository.count()).isEqualTo(2);

        // And after signing in again the student picks up exactly where they left off.
        Device resumed = login(CHROME);
        MvcResult play = mockMvc.perform(get("/api/sessions/" + sessionId + "/play")
                .header("Authorization", "Bearer " + resumed.accessToken()))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode body = objectMapper.readTree(play.getResponse().getContentAsString(StandardCharsets.UTF_8));

        long answered = 0;
        for (JsonNode item : body.get("questions")) {
            if ("ANSWERED".equals(item.get("state").asText())) {
                answered++;
            }
        }
        assertThat(answered).isEqualTo(2);
        assertThat(body.get("status").asText()).isEqualTo("ACTIVE");
    }

    private void answer(Device device, String sessionId, Question q) throws Exception {
        String trueProposition = propositionRepository.findByQuestionId(q.getId()).stream()
            .filter(Proposition::isTrue).findFirst().orElseThrow().getId().toString();
        mockMvc.perform(put("/api/sessions/" + sessionId + "/questions/" + q.getId() + "/answer")
                .header("Authorization", "Bearer " + device.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "selectedPropositionIds", List.of(trueProposition), "secondsSpent", 10))))
            .andExpect(status().isOk());
    }
}
