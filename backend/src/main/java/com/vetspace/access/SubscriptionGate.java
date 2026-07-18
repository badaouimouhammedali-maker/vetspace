package com.vetspace.access;

import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.repository.SubscriptionRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * The paywall. Content endpoints (session create/play, question count, student mindmaps)
 * require an active subscription matching the student's school + study year. ADMIN/TEACHER
 * pass unconditionally. Demo mode is decided by callers on top of hasAccess().
 */
@Component
public class SubscriptionGate {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionGate(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    public boolean hasAccess(User user) {
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.TEACHER) {
            return true;
        }
        if (user.getSchool() == null || user.getStudyYear() == null) {
            return false;
        }
        return subscriptionRepository.hasActiveMatchingSubscription(
            user.getId(), user.getSchool().getId(), user.getStudyYear(), Instant.now());
    }

    public void require(User user) {
        if (!hasAccess(user)) {
            throw new SubscriptionRequiredException();
        }
    }
}
