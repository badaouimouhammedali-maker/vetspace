package com.vetspace.marketing;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vetspace.domain.content.Course;
import com.vetspace.domain.content.ExamType;
import com.vetspace.domain.content.Mindmap;
import com.vetspace.domain.content.Module;
import com.vetspace.domain.content.Question;
import com.vetspace.domain.content.SourceExam;
import com.vetspace.domain.school.School;
import com.vetspace.repository.CourseRepository;
import com.vetspace.repository.MindmapRepository;
import com.vetspace.repository.ModuleRepository;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.SourceExamRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class PublicStatsIntegrationTest {

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
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private ModuleRepository moduleRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private SourceExamRepository sourceExamRepository;
    @Autowired private MindmapRepository mindmapRepository;

    @BeforeEach
    void setUp() {
        School school = schoolRepository.save(School.builder().name("ENSV").slug("ensv-" + UUID.randomUUID()).build());
        Module module = moduleRepository.save(Module.builder()
            .school(school).studyYear(3).name("Anatomie").position(1).published(true).build());
        Course course = courseRepository.save(Course.builder()
            .module(module).name("Ostéologie").position(1).published(true).build());
        questionRepository.save(Question.builder().course(course).statement("Q?").published(true).build());
        sourceExamRepository.save(SourceExam.builder()
            .school(school).label("Examen 2024").year(2024).examType(ExamType.EXAMEN).build());
        mindmapRepository.save(Mindmap.builder()
            .course(course).title("Carte").imageUrl("http://localhost:9000/vetspace/media/x.png").published(true).build());
    }

    @Test
    void publicStatsAreReachableWithoutTokenAndCountPublishedContent() throws Exception {
        mockMvc.perform(get("/api/public/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questions").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.examens").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.mindmaps").value(greaterThanOrEqualTo(1)));
    }
}
