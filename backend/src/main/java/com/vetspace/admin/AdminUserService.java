package com.vetspace.admin;

import com.vetspace.admin.dto.AdminDtos.AdminUserDto;
import com.vetspace.domain.school.School;
import com.vetspace.domain.user.RefreshToken;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.repository.RefreshTokenRepository;
import com.vetspace.repository.SubscriptionRepository;
import com.vetspace.repository.UserRepository;
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

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AdminUserService(UserRepository userRepository, SubscriptionRepository subscriptionRepository,
                             RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
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
            live.forEach(t -> t.setRevokedAt(now));
            refreshTokenRepository.saveAll(live);
        }
        return toDto(userRepository.save(user), Instant.now());
    }

    private AdminUserDto toDto(User u, Instant now) {
        School school = u.getSchool();
        return new AdminUserDto(u.getId(), u.getUsername(), u.getEmail(),
            u.getFirstName() + " " + u.getLastName(), u.getRole(), u.getStatus(),
            school == null ? null : school.getName(), u.getStudyYear(),
            subscriptionRepository.countActiveForUser(u.getId(), now), u.getCreatedAt());
    }
}
