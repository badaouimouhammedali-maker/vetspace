package com.vetspace.admin;

import com.vetspace.admin.dto.AdminDtos.OverviewDto;
import com.vetspace.admin.dto.AdminDtos.RegistrationDto;
import com.vetspace.domain.extras.SignalStatus;
import com.vetspace.domain.school.School;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.SessionRepository;
import com.vetspace.repository.SignalRepository;
import com.vetspace.repository.SubscriptionRepository;
import com.vetspace.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aggregated counters for the admin landing screen. Read-only. */
@Service
@Transactional(readOnly = true)
public class AdminOverviewService {

    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final SessionRepository sessionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SignalRepository signalRepository;

    public AdminOverviewService(UserRepository userRepository, QuestionRepository questionRepository,
                                 SessionRepository sessionRepository, SubscriptionRepository subscriptionRepository,
                                 SignalRepository signalRepository) {
        this.userRepository = userRepository;
        this.questionRepository = questionRepository;
        this.sessionRepository = sessionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.signalRepository = signalRepository;
    }

    public OverviewDto overview() {
        Instant now = Instant.now();
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<RegistrationDto> latest = userRepository.findTop8ByRoleOrderByCreatedAtDesc(Role.STUDENT).stream()
            .map(this::toRegistration)
            .toList();
        return new OverviewDto(
            userRepository.countByRole(Role.STUDENT),
            questionRepository.count(),
            sessionRepository.countByStartedAtGreaterThanEqual(startOfDay),
            subscriptionRepository.countActive(now),
            signalRepository.countByStatus(SignalStatus.OPEN),
            latest);
    }

    private RegistrationDto toRegistration(User u) {
        School school = u.getSchool();
        return new RegistrationDto(u.getId(), u.getUsername(), u.getEmail(),
            u.getFirstName() + " " + u.getLastName(),
            school == null ? null : school.getName(), u.getStudyYear(), u.getCreatedAt());
    }
}
