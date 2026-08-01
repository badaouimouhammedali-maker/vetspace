package com.vetspace.stats;

import com.vetspace.access.SubscriptionGate;
import com.vetspace.admin.QuestionSpecifications;
import com.vetspace.domain.session.QuestionState;
import com.vetspace.domain.session.Session;
import com.vetspace.domain.session.SessionType;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.repository.CourseRepository;
import com.vetspace.repository.CourseRepository.CourseCatalogRow;
import com.vetspace.repository.MindmapRepository;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.SessionQuestionRepository;
import com.vetspace.repository.SessionQuestionRepository.CourseCoverageRow;
import com.vetspace.repository.SessionQuestionRepository.CourseStatsRow;
import com.vetspace.repository.SessionQuestionRepository.SessionProgress;
import com.vetspace.repository.SessionQuestionRepository.WeeklyRow;
import com.vetspace.repository.SessionRepository;
import com.vetspace.repository.SourceExamRepository;
import com.vetspace.repository.SubscriptionRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.stats.dto.StatsDtos.BankTotalsDto;
import com.vetspace.stats.dto.StatsDtos.CourseCoverageDto;
import com.vetspace.stats.dto.StatsDtos.CourseStatsDto;
import com.vetspace.stats.dto.StatsDtos.DailyStatsDto;
import com.vetspace.stats.dto.StatsDtos.ModuleCoverageDto;
import com.vetspace.stats.dto.StatsDtos.OverviewDto;
import com.vetspace.stats.dto.StatsDtos.SessionStatsDto;
import com.vetspace.user.dto.ActiveSubscriptionDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Personal study statistics — always scoped to the caller's own sessions. Days are bucketed in UTC. */
@Service
public class StatsService {

    private final SessionRepository sessionRepository;
    private final SessionQuestionRepository sessionQuestionRepository;
    private final QuestionRepository questionRepository;
    private final SourceExamRepository sourceExamRepository;
    private final MindmapRepository mindmapRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final SubscriptionGate subscriptionGate;

    public StatsService(SessionRepository sessionRepository, SessionQuestionRepository sessionQuestionRepository,
                         QuestionRepository questionRepository, SourceExamRepository sourceExamRepository,
                         MindmapRepository mindmapRepository, SubscriptionRepository subscriptionRepository,
                         UserRepository userRepository, CourseRepository courseRepository,
                         SubscriptionGate subscriptionGate) {
        this.sessionRepository = sessionRepository;
        this.sessionQuestionRepository = sessionQuestionRepository;
        this.questionRepository = questionRepository;
        this.sourceExamRepository = sourceExamRepository;
        this.mindmapRepository = mindmapRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.subscriptionGate = subscriptionGate;
    }

    public List<SessionStatsDto> sessionStats(UUID userId, SessionType type) {
        List<Session> sessions = type == null
            ? sessionRepository.findByUserIdOrderByStartedAtDesc(userId)
            : sessionRepository.findByUserIdAndSessionTypeOrderByStartedAtDesc(userId, type);
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<UUID, SessionProgress> progress = sessionQuestionRepository
            .progressFor(sessions.stream().map(Session::getId).toList()).stream()
            .collect(Collectors.toMap(SessionProgress::getSessionId, Function.identity()));
        return sessions.stream().map(s -> toSessionStats(s, progress.get(s.getId()))).toList();
    }

    public List<CourseStatsDto> byCourse(UUID userId, UUID sessionId) {
        sessionRepository.findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        return sessionQuestionRepository.statsByCourse(sessionId).stream()
            .map(this::toCourseStats)
            .toList();
    }

    /**
     * Lifetime per-course coverage for the caller's own study year: how much of each
     * course they have worked through and how well, grouped by module.
     *
     * <p>Subscription-gated like the rest of the QCM bank — this reports on the paid
     * content, so an unsubscribed student gets 403 SUBSCRIPTION_REQUIRED rather than a
     * catalogue listing of everything they have not bought. (Staff pass the gate, as
     * everywhere.)
     *
     * <p>Two queries rather than one outer-joined monster: the catalogue totals are the
     * same for every student of the year, the interaction counts are per user, and they
     * are indexed from opposite ends. Joining them in Java keeps both plans trivial —
     * see docs/api.md for the measured plan.
     */
    public List<ModuleCoverageDto> courseCoverage(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        subscriptionGate.require(user);

        // Staff have no study year of their own; there is no "their year" to report on.
        Integer studyYear = user.getStudyYear();
        if (studyYear == null) {
            return List.of();
        }

        Map<UUID, CourseCoverageRow> mine = sessionQuestionRepository.coverageByCourse(userId, studyYear).stream()
            .collect(Collectors.toMap(CourseCoverageRow::getCourseId, Function.identity()));

        // LinkedHashMap: publishedCatalogForYear already returns catalogue order, and the
        // grouping must not throw it away — "default order" is one of the sort options.
        Map<UUID, ModuleAccumulator> byModule = new LinkedHashMap<>();
        for (CourseCatalogRow row : courseRepository.publishedCatalogForYear(studyYear)) {
            byModule
                .computeIfAbsent(row.getModuleId(), id -> new ModuleAccumulator(id, row.getModuleName()))
                .courses.add(toCoverage(row, mine.get(row.getCourseId())));
        }
        return byModule.values().stream().map(ModuleAccumulator::toDto).toList();
    }

    private static CourseCoverageDto toCoverage(CourseCatalogRow catalog, CourseCoverageRow mine) {
        long total = catalog.getTotalQuestions();
        // Absent row = a course never touched. Zeroes, not a gap in the list.
        long seen = mine == null ? 0 : mine.getSeenQuestions();
        long answered = mine == null ? 0 : mine.getAnsweredQuestions();
        long correct = mine == null ? 0 : mine.getCorrectQuestions();
        // A course with no questions yet divides by nothing: guard the ratio, not the row.
        double precision = answered == 0 ? 0.0 : round2(correct * 100.0 / answered);
        return new CourseCoverageDto(catalog.getCourseId(), catalog.getCourseName(),
            total, seen, answered, correct, Math.max(0, total - seen), precision);
    }

    /** Mutable while grouping; the module totals are just the sum of its courses. */
    private static final class ModuleAccumulator {
        private final UUID moduleId;
        private final String moduleName;
        private final List<CourseCoverageDto> courses = new ArrayList<>();

        private ModuleAccumulator(UUID moduleId, String moduleName) {
            this.moduleId = moduleId;
            this.moduleName = moduleName;
        }

        private ModuleCoverageDto toDto() {
            long total = courses.stream().mapToLong(CourseCoverageDto::totalQuestions).sum();
            long seen = courses.stream().mapToLong(CourseCoverageDto::seenQuestions).sum();
            return new ModuleCoverageDto(moduleId, moduleName, total, seen, List.copyOf(courses));
        }
    }

    /** Last 7 days including today, one bucket per UTC day, zero-filled. */
    public List<DailyStatsDto> weekly(UUID userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate firstDay = today.minusDays(6);
        Instant from = firstDay.atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<LocalDate, List<WeeklyRow>> byDay = sessionQuestionRepository.interactionsSince(userId, from).stream()
            .collect(Collectors.groupingBy(r -> r.getAnsweredAt().atZone(ZoneOffset.UTC).toLocalDate()));

        return firstDay.datesUntil(today.plusDays(1)).map(day -> {
            List<WeeklyRow> rows = byDay.getOrDefault(day, List.of());
            long juste = rows.stream().filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
            long fausse = rows.stream()
                .filter(r -> r.getState() == QuestionState.ANSWERED && !Boolean.TRUE.equals(r.getIsCorrect())).count();
            long consultees = rows.stream().filter(r -> r.getState() == QuestionState.CONSULTED).count();
            return new DailyStatsDto(day, juste, fausse, consultees);
        }).toList();
    }

    public OverviewDto overview(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));

        BankTotalsDto bank = bankTotals(user);
        SessionStatsDto lastSession = sessionRepository.findFirstByUserIdOrderByStartedAtDesc(userId)
            .map(s -> {
                SessionProgress p = sessionQuestionRepository.progressFor(List.of(s.getId())).stream()
                    .findFirst().orElse(null);
                return toSessionStats(s, p);
            })
            .orElse(null);
        List<ActiveSubscriptionDto> subscriptions = subscriptionRepository.findActiveForUser(userId, Instant.now())
            .stream()
            .map(s -> new ActiveSubscriptionDto(s.getPack().getName(), s.getEndsAt()))
            .toList();
        return new OverviewDto(bank, lastSession, subscriptions);
    }

    /**
     * The question bank is national, so every student sees the same totals. Staff still
     * count with the looser "published question" rule rather than the student rule,
     * which also requires the course and module to be published.
     */
    private BankTotalsDto bankTotals(User user) {
        boolean staff = user.getRole() == Role.ADMIN || user.getRole() == Role.TEACHER;
        return new BankTotalsDto(
            questionRepository.count(staff
                ? QuestionSpecifications.published(true)
                : QuestionSpecifications.visibleToStudent()),
            sourceExamRepository.count(),
            mindmapRepository.countByPublishedTrue());
    }

    private SessionStatsDto toSessionStats(Session s, SessionProgress p) {
        long total = p == null ? s.getQuestionCount() : p.getTotal();
        long answered = p == null ? 0 : p.getAnswered();
        long juste = p == null ? 0 : p.getCorrect();
        long fausse = answered - juste;
        long consulte = p == null ? 0 : p.getConsulted();
        double avg = total == 0 ? 0.0 : round2((double) s.getTotalSeconds() / total);
        double precision = answered == 0 ? 0.0 : round2(juste * 100.0 / answered);
        return new SessionStatsDto(s.getId(), s.getTitle(), s.getSessionType(), s.getStatus(), s.getStartedAt(),
            s.getTotalSeconds(), avg, total, juste, fausse, consulte, precision);
    }

    private CourseStatsDto toCourseStats(CourseStatsRow r) {
        long fausse = r.getAnswered() - r.getCorrect();
        double avg = r.getTotal() == 0 ? 0.0 : round2((double) r.getSeconds() / r.getTotal());
        double precision = r.getAnswered() == 0 ? 0.0 : round2(r.getCorrect() * 100.0 / r.getAnswered());
        return new CourseStatsDto(r.getCourseId(), r.getCourseName(), r.getTotal(), r.getCorrect(), fausse,
            r.getConsulted(), r.getSeconds(), avg, precision);
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
