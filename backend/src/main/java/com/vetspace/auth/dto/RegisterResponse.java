package com.vetspace.auth.dto;

import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.UserStatus;
import java.util.UUID;

public record RegisterResponse(UUID id, String email, String username, Role role, UserStatus status) {
}
