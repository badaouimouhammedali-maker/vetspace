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

    /**
     * @param logins7d      distinct refresh-token FAMILIES created in the last 7 days —
     *                      one per login. Counting tokens would report a single studious
     *                      afternoon as ~30 logins, since a family rotates every 15 min
     * @param distinctIps7d distinct origin addresses over the same window, across
     *                      rotations too. Together these are the sharing signal: a normal
     *                      student is a handful of logins from one or two addresses; an
     *                      account doing the rounds of a class group is neither
     * @param activeSessions live families right now — 1 under the default policy, 0 when
     *                      nobody is signed in
     */
    public record AdminUserDto(UUID id, String username, String email, String fullName, Role role,
                                UserStatus status, String schoolName, Integer studyYear,
                                long activeSubscriptions, Instant createdAt,
                                long logins7d, long distinctIps7d, long activeSessions,
                                String lastDeviceLabel, Instant lastSeenAt) {
    }

    /** Result of "Révoquer la session active": how many live families were closed. */
    public record RevokeSessionsResponse(int revokedSessions) {
    }

    public record UpdateUserStatusRequest(@NotNull UserStatus status) {
    }

    public record SupportMessageDto(UUID id, String userEmail, String username, String fullName,
                                     String subject, String body, Instant createdAt) {
    }
}
