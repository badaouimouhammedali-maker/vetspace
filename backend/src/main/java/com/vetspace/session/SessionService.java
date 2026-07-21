package com.vetspace.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetspace.access.SubscriptionGate;
import com.vetspace.access.SubscriptionRequiredException;
import com.vetspace.admin.QuestionSpecifications;
import com.vetspace.catalog.dto.QuestionPlayDto;
import com.vetspace.domain.content.Proposition;
import com.vetspace.domain.content.Question;
import com.vetspace.domain.session.QuestionState;
import com.vetspace.domain.session.Session;
import com.vetspace.domain.session.SessionQuestion;
import com.vetspace.domain.session.SessionQuestionId;
import com.vetspace.domain.session.SessionStatus;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.repository.ModuleRepository;
import com.vetspace.repository.PropositionRepository;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.SessionAnswerRepository;
import com.vetspace.repository.SessionQuestionRepository;
import com.vetspace.repository.SessionQuestionRepository.SessionProgress;
import com.vetspace.repository.SessionRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.session.dto.SessionDtos.AnswerRequest;
import com.vetspace.session.dto.SessionDtos.CorrectionDto;
import com.vetspace.session.dto.SessionDtos.CreateSessionRequest;
import com.vetspace.session.dto.SessionDtos.PropositionCorrectionDto;
import com.vetspace.session.dto.SessionDtos.RepeatMode;
import com.vetspace.session.dto.SessionDtos.SessionFilters;
import com.vetspace.session.dto.SessionDtos.SessionPlayDto;
import com.vetspace.session.dto.SessionDtos.SessionQuestionPlayDto;
import com.vetspace.session.dto.SessionDtos.SessionSummaryDto;
import com.vetspace.session.dto.SessionDtos.UpdateSessionRequest;
import com.vetspace.domain.session.SessionAnswer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Student session lifecycle. The server is the single source of truth for correctness:
 * answers are evaluated here (exact-set match) and correction data leaves only through
 * the per-question correction endpoints — play DTOs never carry it.
 */
@Service
public class SessionService {

    private static final DateTimeFormatter TITLE_TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SessionRepository sessionRepository;
    private final SessionQuestionRepository sessionQuestionRepository;
    private final SessionAnswerRepository sessionAnswerRepository;
    private final QuestionRepository questionRepository;
    private final PropositionRepository propositionRepository;
    private final ModuleRepository moduleRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final SubscriptionGate subscriptionGate;
    private final int demoQuestionLimit;

    public SessionService(SessionRepository sessionRepository, SessionQuestionRepository sessionQuestionRepository,
                           SessionAnswerRepository sessionAnswerRepository, QuestionRepository questionRepository,
                           PropositionRepository propositionRepository, ModuleRepository moduleRepository,
                           UserRepository userRepository, ObjectMapper objectMapper,
                           SubscriptionGate subscriptionGate,
                           @Value("${app.demo.question-limit}") int demoQuestionLimit) {
        this.sessionRepository = sessionRepository;
        this.sessionQuestionRepository = sessionQuestionRepository;
        this.sessionAnswerRepository = sessionAnswerRepository;
        this.questionRepository = questionRepository;
        this.propositionRepository = propositionRepository;
        this.moduleRepository = moduleRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.subscriptionGate = subscriptionGate;
        this.demoQuestionLimit = demoQuestionLimit;
    }

    // ---------------------------------------------------------------
    // Create
    // ---------------------------------------------------------------

    @Transactional
    public SessionSummaryDto create(UUID userId, CreateSessionRequest request) {
        User user = requireUser(userId);
        SessionFilters filters = request.filters() == null
            ? new SessionFilters(null, null, null, null, null, null) : request.filters();

        // Paywall: unsubscribed students get exactly ONE demo session, restricted to
        // free-preview courses and capped at the demo question limit.
        boolean demo = false;
        if (!subscriptionGate.hasAccess(user)) {
            if (sessionRepository.countByUserId(userId) > 0) {
                throw new SubscriptionRequiredException();
            }
            demo = true;
        }
        int cap = demo ? Math.min(request.questionCount(), demoQuestionLimit) : request.questionCount();
        List<UUID> selected = selectQuestionIds(user, filters, cap, demo);
        Session session = Session.builder()
            .user(user)
            .title(request.title() != null && !request.title().isBlank() ? request.title() : autoTitle(filters))
            .sessionType(request.sessionType())
            .filters(objectMapper.convertValue(filters, new TypeReference<Map<String, Object>>() {
            }))
            .questionCount(selected.size())
            .status(SessionStatus.ACTIVE)
            .startedAt(Instant.now())
            .totalSeconds(0)
            .build();
        session = sessionRepository.save(session);
        attachQuestions(session, selected);
        return toSummary(session, 0, 0);
    }

    private List<UUID> selectQuestionIds(User user, SessionFilters filters, int questionCount, boolean demoOnly) {
        Specification<Question> spec = Specification.allOf(
            QuestionSpecifications.courseIds(filters.courseIds()),
            moduleIdsSpec(filters.moduleIds()),
            sourceExamIdsSpec(filters.sourceExamIds()),
            QuestionSpecifications.difficulty(filters.difficulty()),
            QuestionSpecifications.hasAnyLabel(filters.labelIds(), user.getId()),
            demoOnly ? freePreviewSpec() : null,
            visibilityFor(user));

        List<UUID> candidates = new ArrayList<>(questionRepository.findAll(spec).stream().map(Question::getId).toList());
        if (Boolean.TRUE.equals(filters.onlyUnseen())) {
            candidates.removeAll(sessionQuestionRepository.questionIdsSeenByUser(user.getId()));
        }
        if (candidates.isEmpty()) {
            if (demoOnly) {
                // The filters point at paid content the demo can't reach — signal "subscribe", not "no content".
                throw new SubscriptionRequiredException();
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No questions match the given filters");
        }
        Collections.shuffle(candidates);
        return candidates.subList(0, Math.min(questionCount, candidates.size()));
    }

    private Specification<Question> freePreviewSpec() {
        return (root, query, cb) -> cb.isTrue(root.get("course").get("freePreview"));
    }

    /** Same visibility semantics as GET /api/questions/count, so the builder's count always matches the created session. */
    private Specification<Question> visibilityFor(User user) {
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.TEACHER) {
            return null;
        }
        if (user.getSchool() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No questions match the given filters");
        }
        return QuestionSpecifications.visibleToStudent(user.getSchool().getId());
    }

    private Specification<Question> moduleIdsSpec(List<UUID> moduleIds) {
        return moduleIds == null || moduleIds.isEmpty() ? null
            : (root, query, cb) -> root.get("course").get("module").get("id").in(moduleIds);
    }

    private Specification<Question> sourceExamIdsSpec(List<UUID> sourceExamIds) {
        return sourceExamIds == null || sourceExamIds.isEmpty() ? null
            : (root, query, cb) -> root.get("sourceExam").get("id").in(sourceExamIds);
    }

    private String autoTitle(SessionFilters filters) {
        String prefix = "Session";
        if (filters.moduleIds() != null && !filters.moduleIds().isEmpty()) {
            prefix = moduleRepository.findById(filters.moduleIds().get(0))
                .map(m -> m.getName())
                .orElse(prefix);
        }
        return prefix + " (" + TITLE_TS.format(LocalDateTime.now(ZoneId.systemDefault())) + ")";
    }

    private void attachQuestions(Session session, List<UUID> questionIds) {
        List<Question> questions = questionRepository.findAllById(questionIds);
        Map<UUID, Question> byId = questions.stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        List<SessionQuestion> rows = new ArrayList<>(questionIds.size());
        for (int i = 0; i < questionIds.size(); i++) {
            rows.add(SessionQuestion.builder()
                .id(new SessionQuestionId(session.getId(), questionIds.get(i)))
                .session(session)
                .question(byId.get(questionIds.get(i)))
                .position(i + 1)
                .state(QuestionState.UNANSWERED)
                .secondsSpent(0)
                .build());
        }
        sessionQuestionRepository.saveAll(rows);
    }

    // ---------------------------------------------------------------
    // List
    // ---------------------------------------------------------------

    public Page<SessionSummaryDto> list(UUID userId, Pageable pageable) {
        Page<Session> page = sessionRepository.findByUserId(userId, pageable);
        List<UUID> ids = page.getContent().stream().map(Session::getId).toList();
        Map<UUID, SessionProgress> progress = ids.isEmpty() ? Map.of()
            : sessionQuestionRepository.progressFor(ids).stream()
                .collect(Collectors.toMap(SessionProgress::getSessionId, Function.identity()));
        return page.map(s -> {
            SessionProgress p = progress.get(s.getId());
            return toSummary(s, p == null ? 0 : p.getAnswered(), p == null ? 0 : p.getCorrect());
        });
    }

    // ---------------------------------------------------------------
    // Play
    // ---------------------------------------------------------------

    public SessionPlayDto play(UUID userId, UUID sessionId) {
        Session session = requireOwned(sessionId, userId);
        List<SessionQuestion> rows = sessionQuestionRepository.findBySessionIdOrderByPosition(sessionId);
        // Paywall: without a subscription, only a pure free-preview (demo) session stays playable.
        User user = requireUser(userId);
        if (!subscriptionGate.hasAccess(user)
            && !rows.stream().allMatch(sq -> sq.getQuestion().getCourse().isFreePreview())) {
            throw new SubscriptionRequiredException();
        }
        List<UUID> questionIds = rows.stream().map(sq -> sq.getId().getQuestionId()).toList();

        Map<UUID, List<Proposition>> propositions = questionIds.isEmpty() ? Map.of()
            : propositionRepository.findByQuestionIdInOrderByPositionAsc(questionIds).stream()
                .collect(Collectors.groupingBy(p -> p.getQuestion().getId()));
        Map<UUID, List<UUID>> selectedByQuestion = sessionAnswerRepository.findBySessionId(sessionId).stream()
            .collect(Collectors.groupingBy(sa -> sa.getSessionQuestion().getId().getQuestionId(),
                Collectors.mapping(sa -> sa.getProposition().getId(), Collectors.toList())));

        List<SessionQuestionPlayDto> questions = rows.stream().map(sq -> {
            Question q = sq.getQuestion();
            QuestionPlayDto playDto = new QuestionPlayDto(
                q.getId(), q.getCourse().getId(), q.getStatement(), q.getStatementImages(), q.getDifficulty(),
                propositions.getOrDefault(q.getId(), List.of()).stream()
                    .map(p -> new QuestionPlayDto.PropositionPlayDto(p.getId(), p.getLetter(), p.getText(), p.getPosition()))
                    .toList());
            return new SessionQuestionPlayDto(playDto, sq.getState(), sq.getIsCorrect(), sq.getSecondsSpent(),
                selectedByQuestion.getOrDefault(q.getId(), List.of()), sq.getPosition());
        }).toList();

        return new SessionPlayDto(session.getId(), session.getTitle(), session.getSessionType(), session.getStatus(),
            session.getTotalSeconds(), session.getScore(), questions);
    }

    // ---------------------------------------------------------------
    // Answer / consult
    // ---------------------------------------------------------------

    @Transactional
    public CorrectionDto answer(UUID userId, UUID sessionId, UUID questionId, AnswerRequest request) {
        Session session = requireOwned(sessionId, userId);
        requireActive(session);
        SessionQuestion sq = requireSessionQuestion(sessionId, questionId);

        List<Proposition> propositions = propositionRepository.findByQuestionIdInOrderByPositionAsc(List.of(questionId));
        Set<UUID> validIds = propositions.stream().map(Proposition::getId).collect(Collectors.toSet());
        Set<UUID> selected = new HashSet<>(request.selectedPropositionIds());
        if (!validIds.containsAll(selected)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected propositions do not belong to this question");
        }

        // Server-side evaluation, exact-set match: every true proposition selected, nothing else.
        Set<UUID> trueIds = propositions.stream().filter(Proposition::isTrue).map(Proposition::getId).collect(Collectors.toSet());
        boolean correct = selected.equals(trueIds);

        sessionAnswerRepository.deleteBySessionIdAndQuestionId(sessionId, questionId);
        List<SessionAnswer> answers = propositions.stream()
            .filter(p -> selected.contains(p.getId()))
            .map(p -> SessionAnswer.builder().sessionQuestion(sq).proposition(p).selected(true).build())
            .toList();
        sessionAnswerRepository.saveAll(answers);

        sq.setState(QuestionState.ANSWERED);
        sq.setAnsweredAt(Instant.now());
        sq.setIsCorrect(correct);
        sq.setSecondsSpent(sq.getSecondsSpent() + request.secondsSpent());
        sessionQuestionRepository.save(sq);

        session.setTotalSeconds(session.getTotalSeconds() + request.secondsSpent());
        sessionRepository.save(session);

        return correction(sq, propositions, List.copyOf(selected));
    }

    @Transactional
    public CorrectionDto consult(UUID userId, UUID sessionId, UUID questionId) {
        Session session = requireOwned(sessionId, userId);
        requireActive(session);
        SessionQuestion sq = requireSessionQuestion(sessionId, questionId);
        if (sq.getState() == QuestionState.UNANSWERED) {
            sq.setState(QuestionState.CONSULTED);
            sq.setAnsweredAt(Instant.now());
            sessionQuestionRepository.save(sq);
        }
        List<Proposition> propositions = propositionRepository.findByQuestionIdInOrderByPositionAsc(List.of(questionId));
        List<UUID> selected = sessionAnswerRepository.findBySessionIdAndQuestionId(sessionId, questionId).stream()
            .map(sa -> sa.getProposition().getId())
            .toList();
        return correction(sq, propositions, selected);
    }

    private CorrectionDto correction(SessionQuestion sq, List<Proposition> propositions, List<UUID> selected) {
        List<PropositionCorrectionDto> corrections = propositions.stream()
            .map(p -> new PropositionCorrectionDto(p.getId(), p.getLetter(), p.getText(), p.isTrue(),
                p.getExplanationHtml(), p.getExplanationImages(), p.getPosition()))
            .toList();
        return new CorrectionDto(sq.getId().getQuestionId(), sq.getState(), sq.getIsCorrect(), selected, corrections);
    }

    // ---------------------------------------------------------------
    // Submit / patch
    // ---------------------------------------------------------------

    @Transactional
    public SessionSummaryDto submit(UUID userId, UUID sessionId) {
        Session session = requireOwned(sessionId, userId);
        requireActive(session);
        List<SessionQuestion> rows = sessionQuestionRepository.findBySessionIdOrderByPosition(sessionId);
        long answered = rows.stream().filter(sq -> sq.getState() == QuestionState.ANSWERED).count();
        long correct = rows.stream().filter(sq -> Boolean.TRUE.equals(sq.getIsCorrect())).count();

        BigDecimal score = rows.isEmpty() ? BigDecimal.ZERO
            : BigDecimal.valueOf(correct * 100.0 / rows.size()).setScale(2, RoundingMode.HALF_UP);
        session.setScore(score);
        session.setStatus(SessionStatus.SUBMITTED);
        session.setSubmittedAt(Instant.now());
        session = sessionRepository.save(session);
        return toSummary(session, answered, correct);
    }

    @Transactional
    public SessionSummaryDto patch(UUID userId, UUID sessionId, UpdateSessionRequest request) {
        Session session = requireOwned(sessionId, userId);
        if (request.title() != null && !request.title().isBlank()) {
            session.setTitle(request.title());
        }
        if (request.favorite() != null) {
            session.setFavorite(request.favorite());
        }
        if (request.rating() != null) {
            session.setRating(request.rating());
        }
        session = sessionRepository.save(session);
        SessionProgress p = sessionQuestionRepository.progressFor(List.of(sessionId)).stream().findFirst().orElse(null);
        return toSummary(session, p == null ? 0 : p.getAnswered(), p == null ? 0 : p.getCorrect());
    }

    // ---------------------------------------------------------------
    // Repeat / reset / delete
    // ---------------------------------------------------------------

    @Transactional
    public SessionSummaryDto repeat(UUID userId, UUID sessionId, RepeatMode mode) {
        Session source = requireOwned(sessionId, userId);
        User user = requireUser(userId);
        // Repeat is session creation too; demo mode covers exactly one session, so no demo here.
        subscriptionGate.require(user);
        SessionFilters filters = objectMapper.convertValue(source.getFilters(), SessionFilters.class);

        List<UUID> questionIds;
        if (mode == RepeatMode.SAME_FILTERS) {
            questionIds = selectQuestionIds(user, filters, source.getQuestionCount(), false);
        } else {
            List<SessionQuestion> rows = sessionQuestionRepository.findBySessionIdOrderByPosition(sessionId);
            questionIds = rows.stream()
                .filter(sq -> switch (mode) {
                    case ALL -> true;
                    case WRONG_ONLY -> Boolean.FALSE.equals(sq.getIsCorrect());
                    case UNANSWERED_ONLY -> sq.getState() != QuestionState.ANSWERED;
                    case SAME_FILTERS -> throw new IllegalStateException("handled above");
                })
                .map(sq -> sq.getId().getQuestionId())
                .toList();
            if (questionIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No questions match the requested repeat mode");
            }
        }

        Session repeated = Session.builder()
            .user(user)
            .title(source.getTitle())
            .sessionType(source.getSessionType())
            .filters(source.getFilters())
            .questionCount(questionIds.size())
            .status(SessionStatus.ACTIVE)
            .startedAt(Instant.now())
            .totalSeconds(0)
            .build();
        repeated = sessionRepository.save(repeated);
        attachQuestions(repeated, questionIds);
        return toSummary(repeated, 0, 0);
    }

    @Transactional
    public SessionPlayDto reset(UUID userId, UUID sessionId) {
        Session session = requireOwned(sessionId, userId);
        sessionAnswerRepository.deleteBySessionId(sessionId);
        List<SessionQuestion> rows = sessionQuestionRepository.findBySessionIdOrderByPosition(sessionId);
        for (SessionQuestion sq : rows) {
            sq.setState(QuestionState.UNANSWERED);
            sq.setAnsweredAt(null);
            sq.setIsCorrect(null);
            sq.setSecondsSpent(0);
        }
        sessionQuestionRepository.saveAll(rows);
        session.setStatus(SessionStatus.ACTIVE);
        session.setScore(null);
        session.setSubmittedAt(null);
        session.setTotalSeconds(0);
        session.setStartedAt(Instant.now());
        sessionRepository.save(session);
        return play(userId, sessionId);
    }

    @Transactional
    public void delete(UUID userId, UUID sessionId) {
        Session session = requireOwned(sessionId, userId);
        // DB-level ON DELETE CASCADE removes session_questions and their answers.
        sessionRepository.delete(session);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Session requireOwned(UUID sessionId, UUID userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
    }

    private void requireActive(Session session) {
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is already submitted");
        }
    }

    private SessionQuestion requireSessionQuestion(UUID sessionId, UUID questionId) {
        return sessionQuestionRepository.findById(new SessionQuestionId(sessionId, questionId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not part of this session"));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }

    private SessionSummaryDto toSummary(Session s, long answered, long correct) {
        double percent = answered == 0 ? 0.0
            : BigDecimal.valueOf(correct * 100.0 / answered).setScale(2, RoundingMode.HALF_UP).doubleValue();
        return new SessionSummaryDto(s.getId(), s.getTitle(), s.getSessionType(), s.getStatus(), s.getQuestionCount(),
            answered, correct, percent, s.getTotalSeconds(), s.isFavorite(), s.getRating(), s.getScore(),
            s.getStartedAt(), s.getSubmittedAt());
    }
}
