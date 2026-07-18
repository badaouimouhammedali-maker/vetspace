package com.vetspace.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    private static final String HEX_COLOR = "^#[0-9a-fA-F]{6}$";

    /** Partial update: only non-null fields are applied. @Pattern/@Size skip null values by design. */
    public record UpdateProfileRequest(
        @Size(min = 1, max = 100) String lastName,
        @Size(min = 1, max = 100) String firstName,
        @Size(min = 3, max = 50) String username,
        UUID schoolId,
        @Min(1) @Max(10) Integer studyYear,
        @Pattern(regexp = HEX_COLOR, message = "must be a hex color like #a1b2c3") String themePrimary,
        @Pattern(regexp = HEX_COLOR, message = "must be a hex color like #a1b2c3") String themeSecondary,
        @Pattern(regexp = HEX_COLOR, message = "must be a hex color like #a1b2c3") String themeTertiary
    ) {
    }

    public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 10) String newPassword
    ) {
    }

    public record DeleteAccountRequest(@NotBlank String password) {
    }
}
