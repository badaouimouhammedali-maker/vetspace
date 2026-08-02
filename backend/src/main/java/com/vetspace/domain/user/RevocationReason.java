package com.vetspace.domain.user;

/**
 * Why a refresh token stopped being valid.
 *
 * <p>Without this every revoked token looked the same, so a device evicted by the
 * one-session policy replaying its cookie was indistinguishable from an attacker
 * replaying a stolen rotated token — and both were answered as reuse. The distinction is
 * the difference between "you were signed out on another device" and a security incident.
 */
public enum RevocationReason {

    /** Replaced by its own successor during normal rotation. Replaying one IS reuse. */
    ROTATED,

    /** The family was burned because an already-rotated token came back. */
    REUSE,

    /** Evicted by a newer login — the anti-sharing policy. */
    SUPERSEDED,

    /** The user signed out. */
    LOGOUT,

    /** Credentials changed underneath it (reset or self-service change). */
    PASSWORD_CHANGE,

    /** An admin revoked the session, or disabled the account. */
    ADMIN
}
