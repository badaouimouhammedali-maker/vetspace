package com.vetspace.admin;

import com.vetspace.admin.dto.QuestionDtos.ImportDryRunResult;
import com.vetspace.admin.dto.QuestionDtos.ImportResult;
import com.vetspace.admin.dto.QuestionDtos.ImportRowError;
import com.vetspace.admin.dto.QuestionDtos.ImportRowPreview;
import com.vetspace.admin.dto.QuestionDtos.PropositionAdminDto;
import com.vetspace.admin.dto.QuestionDtos.PropositionRequest;
import com.vetspace.admin.dto.QuestionDtos.QuestionAdminDto;
import com.vetspace.admin.dto.QuestionDtos.QuestionImportRow;
import com.vetspace.admin.dto.QuestionDtos.QuestionRequest;
import com.vetspace.domain.content.Course;
import com.vetspace.domain.content.Difficulty;
import com.vetspace.domain.content.Proposition;
import com.vetspace.domain.content.Question;
import com.vetspace.domain.content.SourceExam;
import com.vetspace.repository.CourseRepository;
import com.vetspace.repository.PropositionRepository;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.SourceExamRepository;
import com.vetspace.security.HtmlSanitizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionAdminService {

    private final QuestionRepository questionRepository;
    private final PropositionRepository propositionRepository;
    private final CourseRepository courseRepository;
    private final SourceExamRepository sourceExamRepository;
    private final HtmlSanitizer htmlSanitizer;

    public QuestionAdminService(QuestionRepository questionRepository, PropositionRepository propositionRepository,
                                 CourseRepository courseRepository, SourceExamRepository sourceExamRepository,
                                 HtmlSanitizer htmlSanitizer) {
        this.questionRepository = questionRepository;
        this.propositionRepository = propositionRepository;
        this.courseRepository = courseRepository;
        this.sourceExamRepository = sourceExamRepository;
        this.htmlSanitizer = htmlSanitizer;
    }

    // ---------------------------------------------------------------
    // CRUD
    // ---------------------------------------------------------------

    @Transactional
    public QuestionAdminDto create(QuestionRequest request) {
        requirePropositionRules(request.propositions());
        // saveAndFlush, not save: inside a transaction `save` on a new entity only calls
        // persist(), and @CreationTimestamp/@UpdateTimestamp are generated at flush. The
        // DTO built from the un-flushed entity carried createdAt/updatedAt = null, which
        // the client rejects as a malformed response — so a question that WAS created
        // reported "Erreur", inviting the admin to save it again and duplicate it.
        Question question = questionRepository.saveAndFlush(buildQuestion(new Question(), request));
        List<Proposition> propositions = propositionRepository.saveAll(buildPropositions(question, request.propositions()));
        return toDto(question, propositions);
    }

    @Transactional
    public QuestionAdminDto update(UUID id, QuestionRequest request) {
        requirePropositionRules(request.propositions());
        Question question = require(id);
        buildQuestion(question, request);
        // Same reason as create(): without the flush, updatedAt still holds the value
        // loaded from the database rather than the one this write just produced.
        question = questionRepository.saveAndFlush(question);
        // Full replace: the request is the complete new proposition set. (Once sessions exist,
        // answered propositions are FK-protected and this delete will 409 — retire via publish instead.)
        propositionRepository.deleteByQuestionId(id);
        List<Proposition> propositions = propositionRepository.saveAll(buildPropositions(question, request.propositions()));
        return toDto(question, propositions);
    }

    public QuestionAdminDto get(UUID id) {
        Question question = require(id);
        return toDto(question, propositionRepository.findByQuestionIdInOrderByPositionAsc(List.of(id)));
    }

    @Transactional
    public void delete(UUID id) {
        questionRepository.delete(require(id));
    }

    @Transactional
    public QuestionAdminDto setPublished(UUID id, boolean published) {
        Question question = require(id);
        question.setPublished(published);
        question = questionRepository.save(question);
        return toDto(question, propositionRepository.findByQuestionIdInOrderByPositionAsc(List.of(id)));
    }

    // ---------------------------------------------------------------
    // Filterable list
    // ---------------------------------------------------------------

    public Page<QuestionAdminDto> list(UUID courseId, UUID moduleId, UUID sourceExamId, Difficulty difficulty,
                                        Boolean published, String q, Pageable pageable) {
        Specification<Question> spec = Specification.allOf(
            QuestionSpecifications.courseId(courseId),
            QuestionSpecifications.moduleId(moduleId),
            QuestionSpecifications.sourceExamId(sourceExamId),
            QuestionSpecifications.difficulty(difficulty),
            QuestionSpecifications.published(published),
            QuestionSpecifications.statementContains(q));
        Page<Question> page = questionRepository.findAll(spec, pageable);

        List<UUID> ids = page.getContent().stream().map(Question::getId).toList();
        Map<UUID, List<Proposition>> byQuestion = ids.isEmpty() ? Map.of()
            : propositionRepository.findByQuestionIdInOrderByPositionAsc(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getQuestion().getId()));
        return page.map(question -> toDto(question, byQuestion.getOrDefault(question.getId(), List.of())));
    }

    // ---------------------------------------------------------------
    // Bulk import
    // ---------------------------------------------------------------

    /** All-or-nothing: every row is validated first; a single bad row fails the whole batch with per-row errors. */
    @Transactional
    public ImportResult importQuestions(List<QuestionImportRow> rows) {
        List<ResolvedRow> resolved = resolveAll(rows);
        List<ImportRowError> errors = resolved.stream().flatMap(r -> r.errors().stream()).toList();
        if (!errors.isEmpty()) {
            throw new ImportValidationException(errors);
        }
        List<UUID> ids = new ArrayList<>();
        for (ResolvedRow row : resolved) {
            Question question = questionRepository.save(buildQuestion(new Question(), row.request()));
            propositionRepository.saveAll(buildPropositions(question, row.request().propositions()));
            ids.add(question.getId());
        }
        return new ImportResult(ids.size(), ids);
    }

    /**
     * Everything {@link #importQuestions} would do, minus the writing.
     *
     * <p>{@code readOnly = true} is not decoration: it makes "changes nothing" a property
     * of the transaction rather than a promise about the code below it, so a future edit
     * that adds a save here fails loudly instead of quietly importing during a dry run.
     */
    @Transactional(readOnly = true)
    public ImportDryRunResult dryRun(List<QuestionImportRow> rows) {
        List<ResolvedRow> resolved = resolveAll(rows);
        List<ImportRowPreview> previews = resolved.stream()
            .filter(r -> r.errors().isEmpty())
            .map(ResolvedRow::preview)
            .toList();
        List<ImportRowError> errors = resolved.stream().flatMap(r -> r.errors().stream()).toList();
        return new ImportDryRunResult(rows.size(), previews.size(), previews, errors);
    }

    /** A row after name resolution: either a usable request + preview, or the reasons why not. */
    private record ResolvedRow(QuestionRequest request, ImportRowPreview preview, List<ImportRowError> errors) {
    }

    private List<ResolvedRow> resolveAll(List<QuestionImportRow> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import body must be a non-empty JSON array");
        }
        List<ResolvedRow> resolved = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            resolved.add(resolveRow(i, rows.get(i)));
        }
        return resolved;
    }

    private ResolvedRow resolveRow(int row, QuestionImportRow raw) {
        List<ImportRowError> errors = new ArrayList<>();
        if (raw == null) {
            errors.add(new ImportRowError(row, "row", "must not be null"));
            return new ResolvedRow(null, null, errors);
        }
        if (raw.statement() == null || raw.statement().isBlank()) {
            errors.add(new ImportRowError(row, "statement", "must not be blank"));
        }
        Course course = resolveCourse(row, raw, errors);
        SourceExam sourceExam = resolveSourceExam(row, raw, errors);
        errors.addAll(propositionRuleErrors(row, raw.propositions()));

        if (!errors.isEmpty()) {
            return new ResolvedRow(null, null, errors);
        }
        QuestionRequest request = new QuestionRequest(course.getId(), raw.statement(), raw.statementImages(),
            sourceExam == null ? null : sourceExam.getId(), raw.difficulty(), raw.published(), raw.propositions());
        ImportRowPreview preview = new ImportRowPreview(row, raw.statement(), course.getId(), course.getName(),
            course.getModule().getName(), course.getModule().getStudyYear(),
            sourceExam == null ? null : sourceExam.getId(), sourceExam == null ? null : sourceExam.getLabel(),
            raw.difficulty(), raw.published(), raw.propositions().size());
        return new ResolvedRow(request, preview, List.of());
    }

    /**
     * Course by id or by name. Names are matched case-insensitively after trimming, and
     * narrowed by moduleName/studyYear when the author supplied them.
     *
     * <p>Several matches is an error, never a guess: "Ostéologie" exists in more than one
     * year of a real catalogue, and picking the first would put a whole file of questions
     * into the wrong course silently. The message names the candidates so the fix is
     * obvious from the error alone.
     */
    private Course resolveCourse(int row, QuestionImportRow raw, List<ImportRowError> errors) {
        String name = trimToNull(raw.courseName());
        boolean hasName = name != null || trimToNull(raw.moduleName()) != null || raw.studyYear() != null;

        if (raw.courseId() != null && hasName) {
            errors.add(new ImportRowError(row, "courseId",
                "provide either courseId or courseName/moduleName/studyYear, not both"));
            return null;
        }
        if (raw.courseId() != null) {
            Course course = courseRepository.findById(raw.courseId()).orElse(null);
            if (course == null) {
                errors.add(new ImportRowError(row, "courseId", "unknown course"));
            }
            return course;
        }
        if (name == null) {
            errors.add(new ImportRowError(row, "courseName", "must not be blank (or supply courseId)"));
            return null;
        }
        List<Course> matches = courseRepository.findByNameForImport(
            name, lowerOrNull(trimToNull(raw.moduleName())), raw.studyYear());
        if (matches.isEmpty()) {
            errors.add(new ImportRowError(row, "courseName",
                "no course named \"" + name + "\"" + narrowing(raw)));
            return null;
        }
        if (matches.size() > 1) {
            errors.add(new ImportRowError(row, "courseName", matches.size() + " courses named \"" + name
                + "\"" + narrowing(raw) + " — " + describe(matches)
                + "; add moduleName/studyYear or use courseId"));
            return null;
        }
        return matches.get(0);
    }

    private SourceExam resolveSourceExam(int row, QuestionImportRow raw, List<ImportRowError> errors) {
        String label = trimToNull(raw.sourceExamLabel());
        if (raw.sourceExamId() != null && label != null) {
            errors.add(new ImportRowError(row, "sourceExamId",
                "provide either sourceExamId or sourceExamLabel, not both"));
            return null;
        }
        if (raw.sourceExamId() != null) {
            SourceExam exam = sourceExamRepository.findById(raw.sourceExamId()).orElse(null);
            if (exam == null) {
                errors.add(new ImportRowError(row, "sourceExamId", "unknown source exam"));
            }
            return exam;
        }
        if (label == null) {
            // Optional field: absent means the question is not tied to an exam.
            return null;
        }
        List<SourceExam> matches = sourceExamRepository.findByLabelIgnoreCaseOrderByYearDesc(label);
        if (matches.isEmpty()) {
            errors.add(new ImportRowError(row, "sourceExamLabel", "no source exam labelled \"" + label + "\""));
            return null;
        }
        if (matches.size() > 1) {
            errors.add(new ImportRowError(row, "sourceExamLabel", matches.size() + " source exams labelled \""
                + label + "\" (" + matches.stream().map(e -> String.valueOf(e.getYear()))
                    .collect(Collectors.joining(", ")) + ") — use sourceExamId"));
            return null;
        }
        return matches.get(0);
    }

    /** " (module X, year N)" — echoes back what the author asked for, so the error reads as a whole sentence. */
    private static String narrowing(QuestionImportRow raw) {
        List<String> parts = new ArrayList<>();
        if (trimToNull(raw.moduleName()) != null) {
            parts.add("module \"" + raw.moduleName().trim() + "\"");
        }
        if (raw.studyYear() != null) {
            parts.add("year " + raw.studyYear());
        }
        return parts.isEmpty() ? "" : " in " + String.join(", ", parts);
    }

    private static String describe(List<Course> courses) {
        return courses.stream()
            .map(c -> c.getModule().getName() + "/year " + c.getModule().getStudyYear())
            .collect(Collectors.joining(", "));
    }

    /** Locale.ROOT, not the platform default: a Turkish JVM lowercases "I" to "ı" and the match silently fails. */
    private static String lowerOrNull(String value) {
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ---------------------------------------------------------------
    // Proposition rules (shared by CRUD and import)
    // ---------------------------------------------------------------

    private void requirePropositionRules(List<PropositionRequest> propositions) {
        List<ImportRowError> errors = propositionRuleErrors(0, propositions);
        if (!errors.isEmpty()) {
            ImportRowError first = errors.get(0);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, first.field() + ": " + first.message());
        }
    }

    private List<ImportRowError> propositionRuleErrors(int row, List<PropositionRequest> propositions) {
        List<ImportRowError> errors = new ArrayList<>();
        if (propositions == null || propositions.size() < 2 || propositions.size() > 5) {
            errors.add(new ImportRowError(row, "propositions", "must contain between 2 and 5 propositions"));
            return errors;
        }
        Set<String> letters = new HashSet<>();
        boolean anyTrue = false;
        boolean anyFalse = false;
        for (int i = 0; i < propositions.size(); i++) {
            PropositionRequest p = propositions.get(i);
            String prefix = "propositions[" + i + "]";
            String letter = p.letter() == null ? "" : p.letter();
            if (!letter.matches("[A-E]")) {
                errors.add(new ImportRowError(row, prefix + ".letter", "must be a single letter A-E"));
            } else if (!letters.add(letter)) {
                errors.add(new ImportRowError(row, prefix + ".letter", "duplicate letter " + letter));
            }
            if (p.text() == null || p.text().isBlank()) {
                errors.add(new ImportRowError(row, prefix + ".text", "must not be blank"));
            }
            if (p.isTrue() == null) {
                errors.add(new ImportRowError(row, prefix + ".isTrue", "must not be null"));
            } else if (p.isTrue()) {
                anyTrue = true;
            } else {
                anyFalse = true;
            }
        }
        if (!anyTrue) {
            errors.add(new ImportRowError(row, "propositions", "at least one proposition must be true"));
        }
        if (!anyFalse) {
            errors.add(new ImportRowError(row, "propositions", "at least one proposition must be false"));
        }
        return errors;
    }

    // ---------------------------------------------------------------
    // Assembly
    // ---------------------------------------------------------------

    private Question buildQuestion(Question question, QuestionRequest request) {
        Course course = courseRepository.findById(request.courseId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        SourceExam sourceExam = null;
        if (request.sourceExamId() != null) {
            sourceExam = sourceExamRepository.findById(request.sourceExamId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source exam not found"));
        }
        question.setCourse(course);
        question.setStatement(request.statement());
        question.setStatementImages(request.statementImages() == null ? new ArrayList<>() : new ArrayList<>(request.statementImages()));
        question.setSourceExam(sourceExam);
        question.setDifficulty(request.difficulty());
        question.setPublished(request.published());
        return question;
    }

    private List<Proposition> buildPropositions(Question question, List<PropositionRequest> requests) {
        List<PropositionRequest> byLetter = requests.stream()
            .sorted(Comparator.comparing(PropositionRequest::letter))
            .toList();
        List<Proposition> result = new ArrayList<>(byLetter.size());
        for (int i = 0; i < byLetter.size(); i++) {
            PropositionRequest p = byLetter.get(i);
            result.add(Proposition.builder()
                .question(question)
                .letter(p.letter())
                .text(p.text())
                .isTrue(p.isTrue())
                .explanationHtml(htmlSanitizer.sanitize(p.explanationHtml()))
                .explanationImages(p.explanationImages() == null ? new ArrayList<>() : new ArrayList<>(p.explanationImages()))
                .position(i + 1)
                .build());
        }
        return result;
    }

    private Question require(UUID id) {
        return questionRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
    }

    private QuestionAdminDto toDto(Question q, List<Proposition> propositions) {
        List<PropositionAdminDto> propositionDtos = propositions.stream()
            .map(p -> new PropositionAdminDto(p.getId(), p.getLetter(), p.getText(), p.isTrue(),
                p.getExplanationHtml(), p.getExplanationImages(), p.getPosition()))
            .toList();
        return new QuestionAdminDto(q.getId(), q.getCourse().getId(), q.getStatement(), q.getStatementImages(),
            q.getSourceExam() != null ? q.getSourceExam().getId() : null, q.getDifficulty(), q.isPublished(),
            q.getCreatedAt(), q.getUpdatedAt(), propositionDtos);
    }
}
