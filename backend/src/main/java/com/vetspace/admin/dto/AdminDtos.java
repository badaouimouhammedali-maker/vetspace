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
        List<RegistrationDto> latestRegistrations,
        List<SchoolBreakdownDto> studentsBySchool
    ) {
    }

    /**
     * Students per école. Content is national, so this is the one place the field still
     * does work: it tells the team where their students actually are.
     *
     * @param schoolName null for the bucket of students with no école recorded
     */
    public record SchoolBreakdownDto(UUID schoolId, String schoolName, long students) {
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
