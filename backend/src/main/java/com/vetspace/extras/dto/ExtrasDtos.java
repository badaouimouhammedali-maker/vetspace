package com.vetspace.extras.dto;

import com.vetspace.domain.extras.NotificationKind;
import com.vetspace.domain.extras.SignalStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ExtrasDtos {

    private ExtrasDtos() {
    }

    // Labels ---------------------------------------------------------

    public record LabelRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex color like #a1b2c3") String color
    ) {
    }

    public record LabelDto(UUID id, String name, String color, long questionCount) {
    }

    // Notes ----------------------------------------------------------

    public record NoteRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String contentHtml,
        UUID questionId,
        UUID courseId
    ) {
    }

    public record NoteDto(UUID id, String title, String contentHtml, UUID questionId, UUID courseId,
                           Instant createdAt, Instant updatedAt) {
    }

    // Signals --------------------------------------------------------

    public record SignalRequest(@NotNull UUID questionId, @NotBlank @Size(max = 2000) String message) {
    }

    public record SignalDto(UUID id, UUID questionId, String questionStatement, String message,
                             SignalStatus status, String adminReply, Instant createdAt, Instant updatedAt) {
    }

    public record SignalAdminDto(UUID id, UUID questionId, String questionStatement, String userEmail,
                                  String message, SignalStatus status, String adminReply,
                                  Instant createdAt, Instant updatedAt) {
    }

    public record SignalReplyRequest(@Size(max = 2000) String reply) {
    }

    // Notifications --------------------------------------------------

    public record NotificationRequest(
        @NotNull NotificationKind kind,
        @NotBlank @Size(max = 255) String title,
        @NotBlank String body,
        UUID schoolId,
        @Min(1) @Max(10) Integer studyYear
    ) {
    }

    public record NotificationDto(UUID id, NotificationKind kind, String title, String body,
                                   Instant createdAt, boolean read) {
    }

    /** Admin broadcast history: shows targeting (null school/year = everyone). */
    public record NotificationAdminDto(UUID id, NotificationKind kind, String title, String body,
                                        UUID schoolId, String schoolName, Integer studyYear, Instant createdAt) {
    }

    // Support --------------------------------------------------------

    public record SupportRequest(@NotBlank @Size(max = 255) String subject, @NotBlank @Size(max = 5000) String body) {
    }
}
