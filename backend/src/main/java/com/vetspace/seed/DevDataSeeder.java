package com.vetspace.seed;

import com.vetspace.access.CodeGenerator;
import com.vetspace.domain.access.ActivationCode;
import com.vetspace.domain.access.Pack;
import com.vetspace.domain.content.Course;
import com.vetspace.domain.content.Difficulty;
import com.vetspace.domain.content.Mindmap;
import com.vetspace.domain.content.Module;
import com.vetspace.domain.content.Proposition;
import com.vetspace.domain.content.Question;
import com.vetspace.domain.school.School;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.repository.ActivationCodeRepository;
import com.vetspace.repository.CourseRepository;
import com.vetspace.repository.MindmapRepository;
import com.vetspace.repository.ModuleRepository;
import com.vetspace.repository.PackRepository;
import com.vetspace.repository.PropositionRepository;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.HtmlSanitizer;
import com.vetspace.security.TokenHasher;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * First-run convenience data for local development: an admin, one school with a small but
 * complete content tree (module, courses, questions with rich explanations, mindmap), one
 * pack and five activation codes. The plaintext codes are logged — dev profile ONLY, an
 * explicit exception to the "never log codes" rule so the whole flow is testable by hand.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final int STUDY_YEAR = 3;
    private static final int QUESTION_COUNT = 15;
    private static final int CODE_COUNT = 5;

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final ModuleRepository moduleRepository;
    private final CourseRepository courseRepository;
    private final QuestionRepository questionRepository;
    private final PropositionRepository propositionRepository;
    private final MindmapRepository mindmapRepository;
    private final PackRepository packRepository;
    private final ActivationCodeRepository activationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final HtmlSanitizer htmlSanitizer;
    private final String adminEmail;
    private final String adminPassword;
    private final String e2eSeedCode;

    public DevDataSeeder(UserRepository userRepository, SchoolRepository schoolRepository,
                          ModuleRepository moduleRepository, CourseRepository courseRepository,
                          QuestionRepository questionRepository, PropositionRepository propositionRepository,
                          MindmapRepository mindmapRepository, PackRepository packRepository,
                          ActivationCodeRepository activationCodeRepository,
                          PasswordEncoder passwordEncoder, HtmlSanitizer htmlSanitizer,
                          @Value("${app.seed.admin-email:}") String adminEmail,
                          @Value("${app.seed.admin-password:}") String adminPassword,
                          @Value("${app.seed.e2e-code:}") String e2eSeedCode) {
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
        this.moduleRepository = moduleRepository;
        this.courseRepository = courseRepository;
        this.questionRepository = questionRepository;
        this.propositionRepository = propositionRepository;
        this.mindmapRepository = mindmapRepository;
        this.packRepository = packRepository;
        this.activationCodeRepository = activationCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.htmlSanitizer = htmlSanitizer;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.e2eSeedCode = e2eSeedCode;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("Skipping dev seed: ADMIN_EMAIL/ADMIN_PASSWORD not set");
            return;
        }

        School school = schoolRepository.save(School.builder()
            .name("ENSV Alger")
            .slug("ensv-alger")
            .build());

        User admin = userRepository.save(User.builder()
            .email(adminEmail)
            .username("admin")
            .passwordHash(passwordEncoder.encode(adminPassword))
            .lastName("Admin")
            .firstName("VetSpace")
            .role(Role.ADMIN)
            .status(UserStatus.ACTIVE)
            .build());

        Module module = moduleRepository.save(Module.builder()
            .school(school).studyYear(STUDY_YEAR).name("Anatomie").position(1).published(true).build());
        Course osteologie = courseRepository.save(Course.builder()
            .module(module).name("Ostéologie").position(1).published(true).freePreview(true).build());
        Course arthrologie = courseRepository.save(Course.builder()
            .module(module).name("Arthrologie").position(2).published(true).freePreview(false).build());

        for (int i = 1; i <= QUESTION_COUNT; i++) {
            seedQuestion(i, i <= 8 ? osteologie : arthrologie);
        }

        mindmapRepository.save(Mindmap.builder()
            .course(osteologie)
            .title("Squelette du chien — vue d'ensemble")
            .imageUrl("http://localhost:9000/vetspace/media/seed-mindmap.png")
            .published(true)
            .build());

        Pack pack = packRepository.save(Pack.builder()
            .school(school)
            .studyYear(STUDY_YEAR)
            .name("Pack 3e année — 2026/2027")
            .academicYear("2026-2027")
            .priceDa(3500)
            .active(true)
            .expiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
            .build());

        List<String> codes = new ArrayList<>(CODE_COUNT);
        for (int i = 0; i < CODE_COUNT; i++) {
            String code = CodeGenerator.generate();
            codes.add(code);
            activationCodeRepository.save(ActivationCode.builder()
                .pack(pack)
                .codeHash(TokenHasher.sha256Hex(CodeGenerator.normalize(code)))
                .maxUses(1).usedCount(0).revoked(false)
                .createdBy(admin)
                .build());
        }

        // Deterministic activation code for automated e2e (set E2E_SEED_CODE only in the e2e stack).
        if (!e2eSeedCode.isBlank()) {
            activationCodeRepository.save(ActivationCode.builder()
                .pack(pack)
                .codeHash(TokenHasher.sha256Hex(CodeGenerator.normalize(e2eSeedCode)))
                .maxUses(1000).usedCount(0).revoked(false)
                .createdBy(admin)
                .build());
        }

        log.info("Seeded dev data: 1 school, 1 module, 2 courses, {} questions, 1 mindmap, 1 pack, {} codes, 1 admin ({})",
            QUESTION_COUNT, CODE_COUNT, adminEmail);
        // Dev-profile-only exception to the no-code-logging rule: these seeded codes exist
        // precisely so the redeem flow can be exercised by hand.
        log.info("[DEV ONLY] Activation codes for '{}': {}", pack.getName(), String.join("  ", codes));
    }

    private void seedQuestion(int index, Course course) {
        Difficulty difficulty = switch (index % 3) {
            case 0 -> Difficulty.HARD;
            case 1 -> Difficulty.EASY;
            default -> Difficulty.MEDIUM;
        };
        Question question = questionRepository.save(Question.builder()
            .course(course)
            .statement("Q" + index + " — À propos de « " + course.getName()
                + " » chez le chien, quelles propositions sont exactes ?")
            .difficulty(difficulty)
            .published(true)
            .build());

        // A and C true, B and D false — every question keeps the same key so manual
        // testing of «juste / fausse» is predictable.
        propositionRepository.save(proposition(question, "A", true, 1,
            "<p>Exact. <span style=\"color: #0F766E\">Point clé :</span> c'est la définition du cours.</p>"
                + "<ul><li>repère anatomique n°" + index + "</li><li>voir schéma du polycopié</li></ul>"));
        propositionRepository.save(proposition(question, "B", false, 2,
            "<p><span style=\"color: #cc0000\">Faux.</span> Confusion classique avec le membre "
                + (index % 2 == 0 ? "thoracique" : "pelvien") + ".</p>"));
        propositionRepository.save(proposition(question, "C", true, 3,
            "<p>Exact — <b>à retenir</b> pour l'examen : <i>l'exception</i> décrite en TD n°" + index + ".</p>"));
        propositionRepository.save(proposition(question, "D", false, 4,
            "<p><span style=\"color: #cc0000\">Faux.</span> La valeur correcte figure dans le tableau récapitulatif.</p>"
                + "<h4>Moyen mnémotechnique</h4><p><u>« OsCar »</u> — Os, Cartilage, Articulation.</p>"));
    }

    private Proposition proposition(Question question, String letter, boolean isTrue, int position, String html) {
        return Proposition.builder()
            .question(question)
            .letter(letter)
            .text("Proposition " + letter + " de la question")
            .isTrue(isTrue)
            .explanationHtml(htmlSanitizer.sanitize(html))
            .position(position)
            .build();
    }
}
