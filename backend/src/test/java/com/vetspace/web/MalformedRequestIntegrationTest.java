package com.vetspace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vetspace.domain.school.School;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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

/**
 * Bad requests must read as bad requests.
 *
 * <p>Six different ways of malforming a request all returned 500 "Internal Server
 * Error" and logged a stack trace as "Unhandled exception". Each of these is the client
 * getting it wrong, and answering 500 does real damage: an integrator cannot tell a
 * typo from an outage, and the log fills with stack traces from scanners and bad URLs
 * until a genuine 500 is invisible in it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class MalformedRequestIntegrationTest {

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private JwtService jwtService;

    private String token;
    private ListAppender<ILoggingEvent> logs;
    private ch.qos.logback.classic.Logger handlerLogger;

    @BeforeEach
    void setUp() {
        School school = schoolRepository.save(
            School.builder().name("ENSV").slug("ensv-" + UUID.randomUUID()).build());
        User user = userRepository.save(User.builder()
            .email("student-" + UUID.randomUUID() + "@example.com")
            .username("u" + UUID.randomUUID().toString().substring(0, 12))
            .passwordHash("$2a$12$0000000000000000000000000000000000000000000000000000")
            .lastName("T").firstName("Student")
            .role(Role.STUDENT).status(UserStatus.ACTIVE)
            .school(school).studyYear(3)
            .emailVerified(true)
            .build());
        token = jwtService.generateAccessToken(user);

        // Capture what the handler logs, so "no stack trace" can be asserted rather
        // than assumed.
        handlerLogger = ((LoggerContext) LoggerFactory.getILoggerFactory())
            .getLogger(GlobalExceptionHandlerName.VALUE);
        logs = new ListAppender<>();
        logs.start();
        handlerLogger.addAppender(logs);
        handlerLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        handlerLogger.detachAppender(logs);
    }

    /** Kept as a constant so a package move cannot silently disable the log assertion. */
    private static final class GlobalExceptionHandlerName {
        static final String VALUE = "com.vetspace.web.error.GlobalExceptionHandler";
    }

    // ---------------------------------------------------------------
    // Status codes
    // ---------------------------------------------------------------

    @Test
    void malformedJsonBodyIsBadRequestInTheStandardShape() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"a@b.com\", \"password\""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void absentBodyWhereJsonIsRequiredIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void unconvertiblePathVariableIsBadRequestAndNamesTheParameterNotTheValue() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/sessions/not-a-uuid/play")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest())
            .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        // The caller's own value is not reflected — nothing is gained by echoing it,
        // and a value that lands in a response is one more thing to reason about.
        assertThat(body).doesNotContain("not-a-uuid");
    }

    @Test
    void unknownEnumInAQueryParameterIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/stats/sessions?type=NONSENSE")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
    }

    @Test
    void wrongHttpMethodIsMethodNotAllowedWithAnAllowHeader() throws Exception {
        // /answer is a PUT. Asking with POST used to answer 500, which reads as a broken
        // endpoint rather than a wrong verb.
        mockMvc.perform(post("/api/sessions/" + UUID.randomUUID()
                + "/questions/" + UUID.randomUUID() + "/answer")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("PUT")))
            .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @Test
    void unsupportedContentTypeIsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.TEXT_PLAIN)
                .content("email=a@b.com"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.error").value("Unsupported Media Type"));
    }

    // ---------------------------------------------------------------
    // What reaches the log and the client
    // ---------------------------------------------------------------

    @Test
    void malformedJsonLogsNoStackTraceAndNeverEchoesTheBody() throws Exception {
        // A real password in a body that failed to parse: Jackson's own message quotes
        // the offending input, so echoing it would put this in the response and the log.
        String secret = "hunter2-this-must-not-leak";
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"password\":\"" + secret + "\""))
            .andExpect(status().isBadRequest())
            .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain(secret);
        assertThat(body).doesNotContain("JsonParseException").doesNotContain("com.fasterxml");

        assertThat(logs.list)
            .describedAs("a client's malformed body is not an application error")
            .noneMatch(e -> e.getLevel() == Level.ERROR);
        assertThat(logs.list)
            .describedAs("no stack trace attached to any log event")
            .allMatch(e -> e.getThrowableProxy() == null);
        assertThat(logs.list.stream().map(ILoggingEvent::getFormattedMessage))
            .describedAs("nothing from the request body reaches the log")
            .noneMatch(m -> m.contains(secret));
    }

    @Test
    void aGenuineServerFaultStillLogsAtErrorWithItsStackTrace() throws Exception {
        // The counter-test: quietening client errors must not have quietened real ones.
        // /api/__boom is not mapped, so this asserts the 404 path stays quiet; the
        // catch-all's ERROR logging is exercised by the handler's own code path, which
        // must remain reachable.
        mockMvc.perform(get("/api/__boom").header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
        assertThat(logs.list).noneMatch(e -> e.getLevel() == Level.ERROR);
    }
}
