package com.vetspace.admin.dto;

import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.UserStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models for the admin console (overview, subscribers, support inbox). Entities are never exposed. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record OverviewDto(
        long students,
        long questions,
        long sessionsToday,
        long activeSubscriptions,
        long openSignals,
        List<RegistrationDto> latestRegistrations
    ) {
    }

    public record RegistrationDto(UUID id, String username, String email, String fullName,
                                   String schoolName, Integer studyYear, Instant createdAt) {
    }

    public record AdminUserDto(UUID id, String username, String email, String fullName, Role role,
                                UserStatus status, String schoolName, Integer studyYear,
                                long activeSubscriptions, Instant createdAt) {
    }

    public record UpdateUserStatusRequest(@NotNull UserStatus status) {
    }

    public record SupportMessageDto(UUID id, String userEmail, String username, String fullName,
                                     String subject, String body, Instant createdAt) {
    }
}
