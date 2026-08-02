package com.vetspace.user;

import com.vetspace.domain.user.RevocationReason;
import com.vetspace.auth.RefreshTokenService;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.media.MediaService;
import com.vetspace.repository.LabelRepository;
import com.vetspace.repository.NoteRepository;
import com.vetspace.repository.PasswordResetTokenRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.user.dto.ProfileDtos.ChangePasswordRequest;
import com.vetspace.user.dto.ProfileDtos.DeleteAccountRequest;
import com.vetspace.user.dto.ProfileDtos.UpdateProfileRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Self-service profile management: partial updates, photo upload, password change, anonymizing deletion. */
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final NoteRepository noteRepository;
    private final LabelRepository labelRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final MediaService mediaService;

    public ProfileService(UserRepository userRepository, SchoolRepository schoolRepository,
                           NoteRepository noteRepository, LabelRepository labelRepository,
                           PasswordResetTokenRepository passwordResetTokenRepository,
                           RefreshTokenService refreshTokenService, PasswordEncoder passwordEncoder,
                           MediaService mediaService) {
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
        this.noteRepository = noteRepository;
        this.labelRepository = labelRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.mediaService = mediaService;
    }

    @Transactional
    public User updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = require(userId);
        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.findByUsername(request.username()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already in use");
            }
            user.setUsername(request.username());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.schoolId() != null) {
            user.setSchool(schoolRepository.findById(request.schoolId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "School not found")));
        }
        if (request.studyYear() != null) {
            user.setStudyYear(request.studyYear());
        }
        if (request.themePrimary() != null) {
            user.setThemePrimary(request.themePrimary());
        }
        if (request.themeSecondary() != null) {
            user.setThemeSecondary(request.themeSecondary());
        }
        if (request.themeTertiary() != null) {
            user.setThemeTertiary(request.themeTertiary());
        }
        return userRepository.save(user);
    }

    @Transactional
    public String updatePhoto(UUID userId, MultipartFile file) {
        User user = require(userId);
        String url = mediaService.upload(file, MediaService.Kind.PROFILE);
        user.setPhotoUrl(url);
        userRepository.save(user);
        return url;
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = require(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        // Other devices' refresh tokens die with the old password.
        refreshTokenService.revokeAllForUser(userId, RevocationReason.PASSWORD_CHANGE);
    }

    /**
     * GDPR-style deletion: the row survives anonymized so session aggregates keep their FK,
     * but nothing identifying remains and the account can never log in again.
     */
    @Transactional
    public void deleteAccount(UUID userId, DeleteAccountRequest request) {
        User user = require(userId);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid password");
        }

        // Personal content goes entirely (labels cascade their question_labels at DB level).
        noteRepository.deleteAll(noteRepository.findByUserId(userId));
        labelRepository.deleteAll(labelRepository.findByUserId(userId));
        passwordResetTokenRepository.deleteByUserId(userId);
        refreshTokenService.revokeAllForUser(userId, RevocationReason.PASSWORD_CHANGE);

        String token = UUID.randomUUID().toString();
        user.setEmail("deleted-" + token + "@anonymized.invalid");
        user.setUsername("deleted-" + token.substring(0, 13));
        user.setLastName("Supprimé");
        user.setFirstName("Utilisateur");
        // Random unusable hash: even with the (now unknowable) email, no password matches.
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setStatus(UserStatus.DISABLED);
        user.setPhotoUrl(null);
        user.setThemePrimary(null);
        user.setThemeSecondary(null);
        user.setThemeTertiary(null);
        userRepository.save(user);
    }

    private User require(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
