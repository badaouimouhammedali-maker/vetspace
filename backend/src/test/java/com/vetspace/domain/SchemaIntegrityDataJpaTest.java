package com.vetspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vetspace.domain.access.ActivationCode;
import com.vetspace.domain.access.Pack;
import com.vetspace.domain.content.Course;
import com.vetspace.domain.content.Module;
import com.vetspace.domain.content.Proposition;
import com.vetspace.domain.content.Question;
import com.vetspace.domain.school.School;
import com.vetspace.domain.session.QuestionState;
import com.vetspace.domain.session.Session;
import com.vetspace.domain.session.SessionQuestion;
import com.vetspace.domain.session.SessionQuestionId;
import com.vetspace.domain.session.SessionStatus;
import com.vetspace.domain.session.SessionType;
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
import com.vetspace.repository.SessionQuestionRepository;
import com.vetspace.repository.SessionRepository;
import com.vetspace.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SchemaIntegrityDataJpaTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private UserRepository userRepository;

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
    private PackRepository packRepository;

    @Autowired
    private ActivationCodeRepository activationCodeRepository;

    // ---------------------------------------------------------------
    // Uniqueness
    // ---------------------------------------------------------------

    @Test
    void userEmailIsUniqueCaseInsensitively() {
        userRepository.saveAndFlush(newUser("Student@Example.com", "student1"));

        User duplicate = newUser("student@example.com", "student2");
        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(duplicate));
    }

    @Test
    void usernameIsUnique() {
        userRepository.saveAndFlush(newUser("first@example.com", "sameusername"));

        User duplicate = newUser("second@example.com", "sameusername");
        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(duplicate));
    }

    @Test
    void activationCodeHashIsUnique() {
        School school = persistSchool();
        Pack pack = packRepository.saveAndFlush(newPack(school));

        activationCodeRepository.saveAndFlush(newActivationCode(pack, "same-hash"));

        ActivationCode duplicate = newActivationCode(pack, "same-hash");
        assertThrows(DataIntegrityViolationException.class, () -> activationCodeRepository.saveAndFlush(duplicate));
    }

    // ---------------------------------------------------------------
    // Cascade
    // ---------------------------------------------------------------

    @Test
    void deletingQuestionCascadesToPropositions() {
        Course course = persistCourse();
        Question question = questionRepository.saveAndFlush(newQuestion(course));
        propositionRepository.saveAndFlush(newProposition(question, "A", 0));
        propositionRepository.saveAndFlush(newProposition(question, "B", 1));
        entityManager.flush();
        entityManager.clear();

        assertThat(propositionRepository.findByQuestionId(question.getId())).hasSize(2);

        questionRepository.deleteById(question.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(questionRepository.findById(question.getId())).isEmpty();
        assertThat(propositionRepository.findByQuestionId(question.getId())).isEmpty();
    }

    // ---------------------------------------------------------------
    // Composite keys
    // ---------------------------------------------------------------

    @Test
    void sessionQuestionCompositeKeyPersistsAndIsFindable() {
        Course course = persistCourse();
        Question question = questionRepository.saveAndFlush(newQuestion(course));
        User user = userRepository.saveAndFlush(newUser("learner@example.com", "learner"));
        Session session = sessionRepository.saveAndFlush(newSession(user));

        SessionQuestionId id = new SessionQuestionId(session.getId(), question.getId());
        SessionQuestion sessionQuestion = SessionQuestion.builder()
            .id(id)
            .session(session)
            .question(question)
            .position(0)
            .state(QuestionState.UNANSWERED)
            .secondsSpent(0)
            .build();
        sessionQuestionRepository.saveAndFlush(sessionQuestion);
        entityManager.clear();

        assertThat(sessionQuestionRepository.findById(id)).isPresent();
        assertThat(sessionQuestionRepository.findById(id).orElseThrow().getPosition()).isZero();
    }

    // ---------------------------------------------------------------
    // jsonb round-trip
    // ---------------------------------------------------------------

    @Test
    void sessionFiltersJsonbRoundTrips() {
        User user = userRepository.saveAndFlush(newUser("filters@example.com", "filtersuser"));
        Session session = newSession(user);
        session.setFilters(Map.of(
            "moduleIds", List.of("m1", "m2"),
            "minDifficulty", "MEDIUM",
            "onlyUnanswered", true
        ));
        sessionRepository.saveAndFlush(session);
        entityManager.clear();

        Session reloaded = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getFilters())
            .containsEntry("minDifficulty", "MEDIUM")
            .containsEntry("onlyUnanswered", true);
        assertThat((List<Object>) reloaded.getFilters().get("moduleIds")).containsExactly("m1", "m2");
    }

    @Test
    void questionStatementImagesJsonbRoundTrips() {
        Course course = persistCourse();
        Question question = newQuestion(course);
        question.setStatementImages(List.of("https://media.vetspace.local/a.png", "https://media.vetspace.local/b.png"));
        questionRepository.saveAndFlush(question);
        entityManager.clear();

        Question reloaded = questionRepository.findById(question.getId()).orElseThrow();
        assertThat(reloaded.getStatementImages())
            .containsExactly("https://media.vetspace.local/a.png", "https://media.vetspace.local/b.png");
    }

    // ---------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------

    private School persistSchool() {
        return schoolRepository.saveAndFlush(School.builder()
            .name("ENSV Alger")
            .slug("ensv-alger-" + System.nanoTime())
            .build());
    }

    private Course persistCourse() {
        School school = persistSchool();
        Module module = moduleRepository.saveAndFlush(Module.builder()
            .school(school)
            .studyYear(3)
            .name("Anatomie")
            .position(0)
            .published(true)
            .build());
        return courseRepository.saveAndFlush(Course.builder()
            .module(module)
            .name("Ostéologie")
            .position(0)
            .published(true)
            .build());
    }

    private Question newQuestion(Course course) {
        return Question.builder()
            .course(course)
            .statement("Quelle est la formule dentaire du chien ?")
            .published(true)
            .build();
    }

    private Proposition newProposition(Question question, String letter, int position) {
        return Proposition.builder()
            .question(question)
            .letter(letter)
            .text("Proposition " + letter)
            .isTrue(position == 0)
            .position(position)
            .build();
    }

    private User newUser(String email, String username) {
        return User.builder()
            .email(email)
            .username(username)
            .passwordHash("$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01")
            .lastName("Doe")
            .firstName("Jane")
            .role(Role.STUDENT)
            .status(UserStatus.ACTIVE)
            .build();
    }

    private Session newSession(User user) {
        return Session.builder()
            .user(user)
            .title("Session d'entraînement")
            .sessionType(SessionType.ENTRAINEMENT)
            .questionCount(10)
            .status(SessionStatus.ACTIVE)
            .startedAt(Instant.now())
            .totalSeconds(0)
            .build();
    }

    private Pack newPack(School school) {
        return Pack.builder()
            .school(school)
            .name("Pack 3e année")
            .academicYear("2026-2027")
            .priceDa(3500)
            .active(true)
            .expiresAt(Instant.now().plusSeconds(365L * 24 * 3600))
            .build();
    }

    private ActivationCode newActivationCode(Pack pack, String codeHash) {
        return ActivationCode.builder()
            .pack(pack)
            .codeHash(codeHash)
            .maxUses(1)
            .usedCount(0)
            .revoked(false)
            .build();
    }
}
