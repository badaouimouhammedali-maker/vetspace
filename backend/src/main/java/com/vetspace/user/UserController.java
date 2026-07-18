package com.vetspace.user;

import com.vetspace.domain.access.Subscription;
import com.vetspace.domain.user.User;
import com.vetspace.repository.SubscriptionRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.user.dto.ActiveSubscriptionDto;
import com.vetspace.user.dto.MeResponse;
import com.vetspace.user.dto.ProfileDtos.ChangePasswordRequest;
import com.vetspace.user.dto.ProfileDtos.DeleteAccountRequest;
import com.vetspace.user.dto.ProfileDtos.UpdateProfileRequest;
import com.vetspace.user.dto.ThemeDto;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("isAuthenticated()")
public class UserController {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ProfileService profileService;

    public UserController(UserRepository userRepository, SubscriptionRepository subscriptionRepository,
                           ProfileService profileService) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toMeResponse(user);
    }

    @PatchMapping("/me")
    public MeResponse update(@AuthenticationPrincipal UUID userId, @Valid @RequestBody UpdateProfileRequest request) {
        return toMeResponse(profileService.updateProfile(userId, request));
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> photo(@AuthenticationPrincipal UUID userId, @RequestParam("file") MultipartFile file) {
        return Map.of("photoUrl", profileService.updatePhoto(userId, file));
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal UUID userId, @Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(userId, request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal UUID userId, @Valid @RequestBody DeleteAccountRequest request) {
        profileService.deleteAccount(userId, request);
    }

    private MeResponse toMeResponse(User user) {
        List<ActiveSubscriptionDto> activeSubscriptions = subscriptionRepository
            .findActiveForUser(user.getId(), Instant.now()).stream()
            .map(this::toActiveSubscriptionDto)
            .toList();
        return new MeResponse(
            user.getId(),
            user.getEmail(),
            user.getUsername(),
            user.getLastName(),
            user.getFirstName(),
            user.getRole(),
            user.getStatus(),
            user.getSchool() != null ? user.getSchool().getId() : null,
            user.getStudyYear(),
            user.getPhotoUrl(),
            new ThemeDto(user.getThemePrimary(), user.getThemeSecondary(), user.getThemeTertiary()),
            activeSubscriptions
        );
    }

    private ActiveSubscriptionDto toActiveSubscriptionDto(Subscription subscription) {
        return new ActiveSubscriptionDto(subscription.getPack().getName(), subscription.getEndsAt());
    }
}
