package com.vetspace.auth;

/**
 * The presented refresh token belonged to a session closed by a newer login elsewhere.
 *
 * <p>Its own type, rather than a 401 with a message, because the frontend has to render a
 * different screen for it: an ordinary expiry is "please sign in again", this is "your
 * account was used on another device and only one is allowed". A student who sees the
 * generic message assumes the app logged them out at random; a student who sees this one
 * knows exactly what happened, and — if it was not them — that someone has their password.
 */
public class SessionSupersededException extends RuntimeException {

    public static final String CODE = "SESSION_SUPERSEDED";

    public SessionSupersededException() {
        super("Session closed by a newer sign-in on another device");
    }
}
