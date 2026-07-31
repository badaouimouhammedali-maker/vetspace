package com.vetspace.catalog;

import com.vetspace.access.SubscriptionGate;
import com.vetspace.admin.QuestionSpecifications;
import com.vetspace.domain.content.Difficulty;
import com.vetspace.domain.content.Question;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Student-facing question queries for the session builder. Subscription gating is NOT applied
 * yet (arrives with the session engine); visibility rules (published-only) are.
 */
@Service
public class QuestionQueryService {

    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final SubscriptionGate subscriptionGate;

    public QuestionQueryService(QuestionRepository questionRepository, UserRepository userRepository,
                                 SubscriptionGate subscriptionGate) {
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.subscriptionGate = subscriptionGate;
    }

    public long count(UUID callerId, List<UUID> courseIds, UUID moduleId, UUID sourceExamId, Difficulty difficulty,
                       List<UUID> labelIds) {
        User caller = userRepository.findById(callerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        subscriptionGate.require(caller);

        Specification<Question> filters = Specification.allOf(
            QuestionSpecifications.courseIds(courseIds),
            QuestionSpecifications.moduleId(moduleId),
            QuestionSpecifications.sourceExamId(sourceExamId),
            QuestionSpecifications.hasAnyLabel(labelIds, callerId),
            QuestionSpecifications.difficulty(difficulty));

        boolean staff = caller.getRole() == Role.ADMIN || caller.getRole() == Role.TEACHER;
        if (staff) {
            return questionRepository.count(filters);
        }
        return questionRepository.count(filters.and(QuestionSpecifications.visibleToStudent()));
    }
}
