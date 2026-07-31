package com.vetspace.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.SubscriptionRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The requirement that replaced per-school content: every school sees the same catalogue,
 * and access is decided by study year alone.
 *
 * <p>These are the tests that would have passed just as well before the change if it were
 * only about deleting a column — they fail on the old behaviour and pass on the new one.
 * Two students at two different écoles are the fixture throughout.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class NationalContentIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("app.recaptcha.enabled", () -> false);
        registry.add("app.auth.auto-verify-emails", () -> true);
    }

    private static final int YEAR = 3;
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private ModuleRepository moduleRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private PackRepository packRepository;
    @Autowired private ActivationCodeRepository activationCodeRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private SubscriptionGate subscriptionGate;

    private String moduleName;
    private User alger;
    private User blida;

    @BeforeEach
    void setUp() {
        School schoolA = schoolRepository.save(School.builder()
            .name("ENSV Alger").slug("alger-" + UUID.randomUUID()).build());
        School schoolB = schoolRepository.save(School.builder()
            .name("ISV Blida").slug("blida-" + UUID.randomUUID()).build());

        // One module, owned by nobody in particular — that is the whole point.
        moduleName = "Anatomie nationale " + UUID.randomUUID();
        Module module = moduleRepository.save(Module.builder()
            .studyYear(YEAR).name(moduleName).position(1).published(true).build());
        courseRepository.save(Course.builder()
            .module(module).name("Ostéologie").position(1).published(true).freePreview(false).build());

        alger = student(schoolA);
        blida = student(schoolB);
    }

    private User student(School school) {
        return userRepository.save(User.builder()
            .email("nat-" + UUID.randomUUID() + "@vetspace.dz")
            .username("nat" + UUID.randomUUID().toString().substring(0, 8))
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .lastName("Test").firstName("National")
            .role(Role.STUDENT).status(UserStatus.ACTIVE).emailVerified(true)
            .school(school).studyYear(YEAR)
            .build());
    }

    private String token(User user) {
        return jwtService.generateAccessToken(user);
    }

    private List<String> moduleNames(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<String> names = new ArrayList<>();
        json.forEach(m -> names.add(m.get("name").asText()));
        return names;
    }

    @Test
    void studentsAtDifferentSchoolsSeeTheSameModules() throws Exception {
        MvcResult fromAlger = mockMvc.perform(get("/api/modules?studyYear=" + YEAR)
                .header("Authorization", "Bearer " + token(alger)))
            .andExpect(status().isOk()).andReturn();
        MvcResult fromBlida = mockMvc.perform(get("/api/modules?studyYear=" + YEAR)
                .header("Authorization", "Bearer " + token(blida)))
            .andExpect(status().isOk()).andReturn();

        // The module was created without any school; both students must find it.
        assertThat(moduleNames(fromAlger)).contains(moduleName);
        assertThat(moduleNames(fromBlida)).contains(moduleName);
    }

    @Test
    void aStudentCanOpenACourseOfAModuleNoSchoolOwns() throws Exception {
        UUID moduleId = moduleRepository.findByStudyYearAndNameIgnoreCase(YEAR, moduleName).orElseThrow().getId();
        // Under the old rules this was a 404 for anyone outside the module's school.
        mockMvc.perform(get("/api/modules/" + moduleId + "/courses")
                .header("Authorization", "Bearer " + token(blida)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Ostéologie"));
    }

    @Test
    void oneNationalPackUnlocksAccessForAnySchool() {
        // A single pack, no school attached, targeting year 3.
        Pack pack = packRepository.save(Pack.builder()
            .studyYear(YEAR).name("Pack national")
            .academicYear("26/" + UUID.randomUUID().toString().substring(0, 8))
            .priceDa(3000).active(true)
            .expiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
            .build());

        assertThat(subscriptionGate.hasAccess(alger)).isFalse();
        assertThat(subscriptionGate.hasAccess(blida)).isFalse();

        subscribe(alger, pack);
        subscribe(blida, pack);

        // Same pack, two écoles, both in — the gate no longer looks at the school.
        assertThat(subscriptionGate.hasAccess(alger)).isTrue();
        assertThat(subscriptionGate.hasAccess(blida)).isTrue();
    }

    @Test
    void theGateStillRefusesAPackForAnotherStudyYear() {
        Pack otherYear = packRepository.save(Pack.builder()
            .studyYear(YEAR + 1).name("Pack 4e")
            .academicYear("26/" + UUID.randomUUID().toString().substring(0, 8))
            .priceDa(3000).active(true)
            .expiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
            .build());
        subscribe(alger, otherYear);

        // Study year is the only dimension left, so it has to actually hold.
        assertThat(subscriptionGate.hasAccess(alger)).isFalse();
    }

    @Test
    void aYearlessPackUnlocksEveryYear() {
        Pack allYears = packRepository.save(Pack.builder()
            .studyYear(null).name("Résidanat")
            .academicYear("26/" + UUID.randomUUID().toString().substring(0, 8))
            .priceDa(5000).active(true)
            .expiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
            .build());
        subscribe(blida, allYears);

        assertThat(subscriptionGate.hasAccess(blida)).isTrue();
    }

    @Test
    void publicPacksAreTheSameListWhicheverSchoolAsks() throws Exception {
        packRepository.save(Pack.builder()
            .studyYear(YEAR).name("Pack visible")
            .academicYear("26/" + UUID.randomUUID().toString().substring(0, 8))
            .priceDa(3000).active(true)
            .expiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
            .build());

        MvcResult result = mockMvc.perform(get("/api/packs?studyYear=" + YEAR))
            .andExpect(status().isOk()).andReturn();
        JsonNode packs = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(packs).isNotEmpty();
        // No school on the wire any more — a client cannot filter by one even if it tried.
        packs.forEach(p -> assertThat(p.has("schoolId")).isFalse());
    }

    private void subscribe(User user, Pack pack) {
        ActivationCode code = activationCodeRepository.save(ActivationCode.builder()
            .pack(pack).codeHash("h-" + UUID.randomUUID()).maxUses(1).usedCount(1).build());
        subscriptionRepository.save(Subscription.builder()
            .user(user).pack(pack).activationCode(code)
            .startsAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .endsAt(Instant.now().plus(365, ChronoUnit.DAYS))
            .build());
    }
}
