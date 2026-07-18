package com.vetspace.user.dto;

import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.UserStatus;
import java.util.List;
import java.util.UUID;

public record MeResponse(
    UUID id,
    String email,
    String username,
    String lastName,
    String firstName,
    Role role,
    UserStatus status,
    UUID schoolId,
    Integer studyYear,
    String photoUrl,
    ThemeDto theme,
    List<ActiveSubscriptionDto> activeSubscriptions
) {
}
