package com.vetspace.admin;

import com.vetspace.admin.dto.AdminDtos.AdminUserDto;
import com.vetspace.admin.dto.AdminDtos.RevokeSessionsResponse;
import com.vetspace.auth.RefreshTokenService;
import com.vetspace.domain.school.School;
import com.vetspace.domain.user.RefreshToken;
import com.vetspace.domain.user.RevocationReason;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.repository.RefreshTokenRepository;
import com.vetspace.repository.RefreshTokenRepository.ActivitySummary;
import com.vetspace.repository.SubscriptionRepository;
import com.vetspace.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** "Abonnés": student directory + enable/disable. Disabling revokes live refresh tokens so access ends at once. */
@Service
public class AdminUserService {

    /** The window the sharing signal is computed over; see AdminUserDto. */
    private static final Duration SHARING_WINDOW = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;

    public AdminUserService(UserRepository userRepository, SubscriptionRepository subscriptionRepository,
                             RefreshTokenRepository refreshTokenRepository,
                             RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserDto> search(String query, Pageable pageable) {
        Instant now = Instant.now();
        return userRepository.searchStudents(query, pageable).map(u -> toDto(u, now));
    }

    /** ADMIN only (enforced at the controller). An admin cannot disable their own account. */
    @Transactional
    public AdminUserDto updateStatus(UUID adminId, UUID targetId, UserStatus status) {
        if (adminId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot change your own account status");
        }
        User user = userRepository.findById(targetId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setStatus(status);
        if (status == UserStatus.DISABLED) {
            List<RefreshToken> live = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(targetId);
            Instant now = Instant.now();
            live.forEach(t -> {
                t.setRevokedAt(now);
                t.setRevokedReason(RevocationReason.ADMIN);
            });
            refreshTokenRepository.saveAll(live);
        }
        return toDto(userRepository.save(user), Instant.now());
    }

    /**
     * Closes every live session of a student without touching their account.
     *
     * <p>Separate from disabling: disabling locks someone out until an admin relents,
     * whereas this ends the current sessions and lets them sign in again — the right
     * response to "my account is being used by someone else", and the wrong one to
     * fraud.
     */
    @Transactional
    public RevokeSessionsResponse revokeSessions(UUID targetId) {
        userRepository.findById(targetId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new RevokeSessionsResponse(
            refreshTokenService.revokeActiveSessions(targetId, RevocationReason.ADMIN));
    }

    private AdminUserDto toDto(User u, Instant now) {
        School school = u.getSchool();
        // Per row, so it stays correct as the page is filtered and sorted. The window is
        // small (one user, 7 days) and served by idx_refresh_tokens_user_created.
        ActivitySummary activity = refreshTokenRepository
            .summariseActivitySince(u.getId(), now.minus(SHARING_WINDOW));
        RefreshToken live = refreshTokenRepository.findMostRecentLive(u.getId()).orElse(null);
        long activeSessions = refreshTokenRepository.findLiveFamilies(u.getId()).size();
        return new AdminUserDto(u.getId(), u.getUsername(), u.getEmail(),
            u.getFirstName() + " " + u.getLastName(), u.getRole(), u.getStatus(),
            school == null ? null : school.getName(), u.getStudyYear(),
            subscriptionRepository.countActiveForUser(u.getId(), now), u.getCreatedAt(),
            activity.getLogins(), activity.getDistinctIps(), activeSessions,
            live == null ? null : live.getDeviceLabel(),
            live == null ? null : (live.getLastUsedAt() != null ? live.getLastUsedAt() : live.getCreatedAt()));
    }
}
