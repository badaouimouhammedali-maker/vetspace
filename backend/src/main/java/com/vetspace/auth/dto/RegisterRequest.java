package com.vetspace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RegisterRequest(
    @NotBlank String lastName,
    @NotBlank String firstName,
    @NotBlank @Size(min = 3, max = 50) String username,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 10) String password,
    @NotNull UUID schoolId,
    @NotNull @Min(1) @Max(10) Integer studyYear,
    String recaptchaToken
) {
}
