package com.vetspace.auth.dto;

import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.UserStatus;
import java.util.UUID;

/**
 * @param verificationEmailSent whether the student has anything to act on regarding
 *                              their verification email. {@code false} means the account
 *                              was created but the message could not be handed to the
 *                              mail server, so the UI should say so and offer "renvoyer"
 *                              rather than sending them to an inbox that will stay
 *                              empty. {@code true} when the mail went out — and also
 *                              when AUTO_VERIFY_EMAILS is on, where there is no message
 *                              to send and nothing for the user to do.
 */
public record RegisterResponse(UUID id, String email, String username, Role role, UserStatus status,
                                boolean verificationEmailSent) {
}
