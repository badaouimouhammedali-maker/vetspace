package com.vetspace.admin;

import com.vetspace.admin.dto.QuestionDtos.ImportResult;
import com.vetspace.admin.dto.QuestionDtos.ImportRowError;
import com.vetspace.admin.dto.QuestionDtos.PropositionAdminDto;
import com.vetspace.admin.dto.QuestionDtos.PropositionRequest;
import com.vetspace.admin.dto.QuestionDtos.QuestionAdminDto;
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
    public ImportResult importQuestions(List<QuestionRequest> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import body must be a non-empty JSON array");
        }
        List<ImportRowError> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            validateRow(i, rows.get(i), errors);
        }
        if (!errors.isEmpty()) {
            throw new ImportValidationException(errors);
        }
        List<UUID> ids = new ArrayList<>();
        for (QuestionRequest row : rows) {
            Question question = questionRepository.save(buildQuestion(new Question(), row));
            propositionRepository.saveAll(buildPropositions(question, row.propositions()));
            ids.add(question.getId());
        }
        return new ImportResult(ids.size(), ids);
    }

    private void validateRow(int row, QuestionRequest request, List<ImportRowError> errors) {
        if (request.statement() == null || request.statement().isBlank()) {
            errors.add(new ImportRowError(row, "statement", "must not be blank"));
        }
        if (request.courseId() == null) {
            errors.add(new ImportRowError(row, "courseId", "must not be null"));
        } else if (courseRepository.findById(request.courseId()).isEmpty()) {
            errors.add(new ImportRowError(row, "courseId", "unknown course"));
        }
        if (request.sourceExamId() != null && sourceExamRepository.findById(request.sourceExamId()).isEmpty()) {
            errors.add(new ImportRowError(row, "sourceExamId", "unknown source exam"));
        }
        errors.addAll(propositionRuleErrors(row, request.propositions()));
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
