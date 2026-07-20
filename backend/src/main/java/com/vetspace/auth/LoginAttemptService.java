package com.vetspace.auth;

import com.vetspace.domain.user.User;
import com.vetspace.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-account lockout: {@value #MAX_ATTEMPTS} failed logins inside {@link #WINDOW} lock the
 * account for {@link #LOCK_DURATION}.
 *
 * <p>This complements the IP-based rate limiter rather than duplicating it. That one caps
 * requests from a single address; it does nothing about a slow attack spread across many
 * addresses against one account, which is the shape credential-stuffing actually takes.
 *
 * <p>State lives on the user row, so it survives restarts and holds across instances.
 */
@Service
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;

    public LoginAttemptService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** True while the account is locked; a lapsed lock is cleared as a side effect. */
    @Transactional
    public boolean isLocked(User user) {
        Instant lockedUntil = user.getLockedUntil();
        if (lockedUntil == null) {
            return false;
        }
        if (lockedUntil.isAfter(Instant.now())) {
            return true;
        }
        // The lock has expired: reset so the next failure starts a fresh window.
        clear(user);
        return false;
    }

    /**
     * Records a failed attempt and locks the account once the threshold is reached.
     *
     * <p>REQUIRES_NEW because the caller throws to signal "invalid credentials"; in the
     * caller's transaction that rollback would discard the very counter increment that makes
     * lockout work, and the limit would never be reached.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(User user) {
        Instant now = Instant.now();
        Instant windowStart = user.getFirstFailedLoginAt();

        if (windowStart == null || windowStart.plus(WINDOW).isBefore(now)) {
            // First failure, or the previous window has lapsed — start counting again.
            user.setFirstFailedLoginAt(now);
            user.setFailedLoginAttempts(1);
        } else {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        }

        if (user.getFailedLoginAttempts() >= MAX_ATTEMPTS) {
            user.setLockedUntil(now.plus(LOCK_DURATION));
            // Counter resets with the lock, so the next failure after it expires starts over.
            user.setFailedLoginAttempts(0);
            user.setFirstFailedLoginAt(null);
        }
        userRepository.save(user);
    }

    /** Clears all failure state; called on a successful login and on lock expiry. */
    @Transactional
    public void clear(User user) {
        if (user.getFailedLoginAttempts() == 0 && user.getFirstFailedLoginAt() == null
            && user.getLockedUntil() == null) {
            return; // nothing to write on the common path
        }
        user.setFailedLoginAttempts(0);
        user.setFirstFailedLoginAt(null);
        user.setLockedUntil(null);
        userRepository.save(user);
    }
}
