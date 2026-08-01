package com.vetspace.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vetspace.domain.content.Course;
import com.vetspace.domain.content.ExamType;
import com.vetspace.domain.content.Module;
import com.vetspace.domain.content.SourceExam;
import com.vetspace.domain.school.School;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.repository.CourseRepository;
import com.vetspace.repository.ModuleRepository;
import com.vetspace.repository.PropositionRepository;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.SourceExamRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.JwtService;
import java.nio.charset.StandardCharsets;
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
 * Bulk import by human-readable name, and the dry run.
 *
 * <p>The catalogue is wiped and rebuilt per method, unlike the sibling question suite:
 * name resolution is a global query, so a course left behind by another test would make
 * "is this name ambiguous?" depend on execution order — which is exactly the bug this
 * feature is supposed to detect, and it must not be the thing that makes the tests flaky.
 *
 * <p>The fixture deliberately contains a real ambiguity: "Parvovirose" exists in
 * "Maladies infectieuses" in BOTH year 3 and year 4, which is what a national catalogue
 * spanning several years actually looks like.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class QuestionImportByNameIntegrationTest {

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
    @Autowired private SourceExamRepository sourceExamRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;

    private static final String INFECTIEUSES = "Maladies infectieuses";

    private Course parvoY3;
    private Course parvoY4;
    private Course leptoY3;
    private SourceExam rattrapageUnique;
    private String adminToken;

    @BeforeEach
    void setUp() {
        propositionRepository.deleteAll();
        questionRepository.deleteAll();
        courseRepository.deleteAll();
        moduleRepository.deleteAll();
        sourceExamRepository.deleteAll();

        Module infectieusesY3 = module(INFECTIEUSES, 3);
        Module infectieusesY4 = module(INFECTIEUSES, 4);
        parvoY3 = course(infectieusesY3, "Parvovirose");
        parvoY4 = course(infectieusesY4, "Parvovirose");
        leptoY3 = course(infectieusesY3, "Leptospirose");

        rattrapageUnique = exam("Session normale 2025", 2025);
        // Same label, two different years: labels are not unique in the schema either.
        exam("Rattrapage", 2024);
        exam("Rattrapage", 2023);

        School school = schoolRepository.save(School.builder()
            .name("ENSV").slug("ensv-" + UUID.randomUUID()).build());
        User admin = userRepository.save(User.builder()
            .email("admin-" + UUID.randomUUID() + "@example.com")
            .username("a" + UUID.randomUUID().toString().substring(0, 12))
            .passwordHash("$2a$12$0000000000000000000000000000000000000000000000000000")
            .lastName("T").firstName("A")
            .role(Role.ADMIN).status(UserStatus.ACTIVE)
            .school(school)
            .emailVerified(true)
            .build());
        adminToken = jwtService.generateAccessToken(admin);
    }

    private Module module(String name, int studyYear) {
        return moduleRepository.save(Module.builder()
            .studyYear(studyYear).name(name).position(1).published(true).build());
    }

    private Course course(Module module, String name) {
        return courseRepository.save(Course.builder()
            .module(module).name(name).position(1).published(true).build());
    }

    private SourceExam exam(String label, int year) {
        return sourceExamRepository.save(SourceExam.builder()
            .label(label).year(year).examType(ExamType.EXAMEN).build());
    }

    // ---------------------------------------------------------------
    // Payload helpers
    // ---------------------------------------------------------------

    /** A structurally valid row with no course identity yet — each test adds its own. */
    private ObjectNode row(String statement) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("statement", statement);
        node.put("published", true);
        ArrayNode propositions = node.putArray("propositions");
        ObjectNode a = propositions.addObject();
        a.put("letter", "A");
        a.put("text", "Vrai");
        a.put("isTrue", true);
        ObjectNode b = propositions.addObject();
        b.put("letter", "B");
        b.put("text", "Faux");
        b.put("isTrue", false);
        return node;
    }

    private ArrayNode rows(ObjectNode... nodes) {
        ArrayNode array = objectMapper.createArrayNode();
        for (ObjectNode node : nodes) {
            array.add(node);
        }
        return array;
    }

    private MvcResult postImport(ArrayNode body, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/admin/questions/import")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().is(expectedStatus))
            .andReturn();
    }

    private JsonNode postDryRun(ArrayNode body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/questions/import/dry-run")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode errorsOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("errors");
    }

    private UUID onlyQuestionsCourseId() {
        return questionRepository.findAll().stream().findFirst().orElseThrow().getCourse().getId();
    }

    // ---------------------------------------------------------------
    // Name resolution
    // ---------------------------------------------------------------

    @Test
    void resolvesCourseByNameModuleAndYear() throws Exception {
        ObjectNode row = row("Parvovirose : transmission");
        row.put("courseName", "Parvovirose");
        row.put("moduleName", INFECTIEUSES);
        row.put("studyYear", 3);

        postImport(rows(row), 201);

        assertThat(questionRepository.count()).isEqualTo(1);
        assertThat(onlyQuestionsCourseId()).isEqualTo(parvoY3.getId());
    }

    @Test
    void studyYearAloneIsEnoughToDisambiguate() throws Exception {
        // Same course name in both years; only the year separates them.
        ObjectNode row = row("Parvovirose en 4e année");
        row.put("courseName", "Parvovirose");
        row.put("studyYear", 4);

        postImport(rows(row), 201);

        assertThat(onlyQuestionsCourseId()).isEqualTo(parvoY4.getId());
    }

    @Test
    void aUniqueCourseNameNeedsNoNarrowing() throws Exception {
        ObjectNode row = row("Leptospirose : réservoir");
        row.put("courseName", "Leptospirose");

        postImport(rows(row), 201);

        assertThat(onlyQuestionsCourseId()).isEqualTo(leptoY3.getId());
    }

    @Test
    void nameMatchingIsCaseInsensitiveAndTrimmed() throws Exception {
        ObjectNode row = row("Casse et espaces");
        row.put("courseName", "  lEpToSpIrOsE  ");

        postImport(rows(row), 201);

        assertThat(onlyQuestionsCourseId()).isEqualTo(leptoY3.getId());
    }

    @Test
    void resolvesSourceExamByLabel() throws Exception {
        ObjectNode row = row("Avec épreuve source");
        row.put("courseName", "Leptospirose");
        row.put("sourceExamLabel", "Session normale 2025");

        postImport(rows(row), 201);

        assertThat(questionRepository.findAll().get(0).getSourceExam().getId())
            .isEqualTo(rattrapageUnique.getId());
    }

    @Test
    void uuidFormStillWorks() throws Exception {
        // The whole point is that names are an ALTERNATIVE; existing UUID files must not break.
        ObjectNode row = row("Par identifiant");
        row.put("courseId", parvoY3.getId().toString());

        postImport(rows(row), 201);

        assertThat(onlyQuestionsCourseId()).isEqualTo(parvoY3.getId());
    }

    // ---------------------------------------------------------------
    // Ambiguity and other resolution failures
    // ---------------------------------------------------------------

    @Test
    void ambiguousCourseNameIsAnErrorAndImportsNothing() throws Exception {
        ObjectNode good = row("Bonne ligne");
        good.put("courseName", "Leptospirose");
        ObjectNode ambiguous = row("Ligne ambiguë");
        ambiguous.put("courseName", "Parvovirose"); // exists in year 3 AND year 4

        JsonNode errors = errorsOf(postImport(rows(good, ambiguous), 400));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).get("row").asInt()).isEqualTo(1);
        assertThat(errors.get(0).get("field").asText()).isEqualTo("courseName");
        String message = errors.get(0).get("message").asText();
        // Names the count, the candidates and the fix — an error you can act on without
        // opening the admin console to work out what "ambiguous" meant.
        assertThat(message).contains("2 courses named \"Parvovirose\"");
        assertThat(message).contains(INFECTIEUSES + "/year 3");
        assertThat(message).contains(INFECTIEUSES + "/year 4");
        assertThat(message).contains("add moduleName/studyYear or use courseId");
        // All-or-nothing: the good row 0 is not persisted either.
        assertThat(questionRepository.count()).isZero();
    }

    @Test
    void ambiguousSourceExamLabelIsAnError() throws Exception {
        ObjectNode row = row("Épreuve ambiguë");
        row.put("courseName", "Leptospirose");
        row.put("sourceExamLabel", "Rattrapage"); // 2024 and 2023

        JsonNode errors = errorsOf(postImport(rows(row), 400));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).get("field").asText()).isEqualTo("sourceExamLabel");
        assertThat(errors.get(0).get("message").asText())
            .contains("2 source exams labelled \"Rattrapage\"")
            .contains("use sourceExamId");
        assertThat(questionRepository.count()).isZero();
    }

    @Test
    void unknownCourseNameReportsWhatWasSearchedFor() throws Exception {
        ObjectNode row = row("Cours inexistant");
        row.put("courseName", "Trypanosomose");
        row.put("studyYear", 3);

        JsonNode errors = errorsOf(postImport(rows(row), 400));

        assertThat(errors.get(0).get("field").asText()).isEqualTo("courseName");
        assertThat(errors.get(0).get("message").asText())
            .isEqualTo("no course named \"Trypanosomose\" in year 3");
    }

    @Test
    void supplyingBothIdAndNameIsRejectedRatherThanSilentlyPreferringOne() throws Exception {
        // A name that disagrees with the id would otherwise sit unnoticed in a file while
        // every question landed in the course the id points at.
        ObjectNode row = row("Les deux formes");
        row.put("courseId", parvoY3.getId().toString());
        row.put("courseName", "Leptospirose");

        JsonNode errors = errorsOf(postImport(rows(row), 400));

        assertThat(errors.get(0).get("field").asText()).isEqualTo("courseId");
        assertThat(errors.get(0).get("message").asText())
            .isEqualTo("provide either courseId or courseName/moduleName/studyYear, not both");
        assertThat(questionRepository.count()).isZero();
    }

    @Test
    void missingCourseIdentityIsAnError() throws Exception {
        JsonNode errors = errorsOf(postImport(rows(row("Sans cours")), 400));

        assertThat(errors.get(0).get("field").asText()).isEqualTo("courseName");
        assertThat(errors.get(0).get("message").asText()).contains("or supply courseId");
    }

    // ---------------------------------------------------------------
    // Dry run
    // ---------------------------------------------------------------

    @Test
    void dryRunReportsResolutionAndWritesNothing() throws Exception {
        ObjectNode first = row("Parvovirose : signes cliniques");
        first.put("courseName", "Parvovirose");
        first.put("studyYear", 3);
        first.put("difficulty", "HARD");
        ObjectNode second = row("Leptospirose : prophylaxie");
        second.put("courseName", "Leptospirose");
        second.put("sourceExamLabel", "Session normale 2025");

        JsonNode body = postDryRun(rows(first, second));

        assertThat(body.get("rowsSubmitted").asInt()).isEqualTo(2);
        assertThat(body.get("wouldImport").asInt()).isEqualTo(2);
        assertThat(body.get("errors")).isEmpty();

        JsonNode row0 = body.get("resolved").get(0);
        assertThat(row0.get("row").asInt()).isZero();
        // The names are echoed back, which is the only way to catch a file that is valid
        // and aimed at the wrong course.
        assertThat(row0.get("courseId").asText()).isEqualTo(parvoY3.getId().toString());
        assertThat(row0.get("courseName").asText()).isEqualTo("Parvovirose");
        assertThat(row0.get("moduleName").asText()).isEqualTo(INFECTIEUSES);
        assertThat(row0.get("studyYear").asInt()).isEqualTo(3);
        assertThat(row0.get("difficulty").asText()).isEqualTo("HARD");
        assertThat(row0.get("propositionCount").asInt()).isEqualTo(2);

        JsonNode row1 = body.get("resolved").get(1);
        assertThat(row1.get("sourceExamLabel").asText()).isEqualTo("Session normale 2025");

        // The point of the whole feature.
        assertThat(questionRepository.count()).isZero();
        assertThat(propositionRepository.count()).isZero();
    }

    @Test
    void dryRunReportsEveryErrorAtOnceAndStillWritesNothing() throws Exception {
        ObjectNode good = row("Bonne ligne");
        good.put("courseName", "Leptospirose");
        ObjectNode ambiguous = row("Ambiguë");
        ambiguous.put("courseName", "Parvovirose");
        ObjectNode unknown = row("Inconnue");
        unknown.put("courseName", "Trypanosomose");

        JsonNode body = postDryRun(rows(good, ambiguous, unknown));

        assertThat(body.get("rowsSubmitted").asInt()).isEqualTo(3);
        // The valid row is still reported as importable, so the author can see how far the
        // file got — unlike the real import, which is all-or-nothing.
        assertThat(body.get("wouldImport").asInt()).isEqualTo(1);
        assertThat(body.get("resolved")).hasSize(1);
        assertThat(body.get("resolved").get(0).get("row").asInt()).isZero();

        JsonNode errors = body.get("errors");
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).get("row").asInt()).isEqualTo(1);
        assertThat(errors.get(1).get("row").asInt()).isEqualTo(2);

        assertThat(questionRepository.count()).isZero();
    }

    @Test
    void dryRunAfterAnActualImportLeavesTheImportedRowsAlone() throws Exception {
        ObjectNode real = row("Déjà importée");
        real.put("courseName", "Leptospirose");
        postImport(rows(real), 201);
        assertThat(questionRepository.count()).isEqualTo(1);

        ObjectNode preview = row("Simulée");
        preview.put("courseName", "Leptospirose");
        JsonNode body = postDryRun(rows(preview));

        assertThat(body.get("wouldImport").asInt()).isEqualTo(1);
        // Still exactly the one real question: the dry run neither added nor removed.
        assertThat(questionRepository.count()).isEqualTo(1);
    }

    @Test
    void dryRunIsAdminOnly() throws Exception {
        mockMvc.perform(post("/api/admin/questions/import/dry-run")
                .contentType(MediaType.APPLICATION_JSON).content("[]"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyBodyIsRejectedOnBothPaths() throws Exception {
        mockMvc.perform(post("/api/admin/questions/import/dry-run")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("[]"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Import body must be a non-empty JSON array"));
        mockMvc.perform(post("/api/admin/questions/import")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("[]"))
            .andExpect(status().isBadRequest());
    }
}
