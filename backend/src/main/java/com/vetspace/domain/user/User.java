package com.vetspace.domain.user;

import com.vetspace.domain.common.AuditableEntity;
import com.vetspace.domain.school.School;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    @Column(name = "study_year")
    private Integer studyYear;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "theme_primary", length = 20)
    private String themePrimary;

    @Column(name = "theme_secondary", length = 20)
    private String themeSecondary;

    @Column(name = "theme_tertiary", length = 20)
    private String themeTertiary;

    /**
     * Failed-login tracking for per-account lockout. Persisted rather than held in memory so
     * it survives restarts and applies across instances — an in-memory counter resets on
     * every deploy, which an attacker only has to outlast.
     */
    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /** Start of the window the current attempt count belongs to. */
    @Column(name = "first_failed_login_at")
    private Instant firstFailedLoginAt;

    /** Non-null and in the future means the account is locked. */
    @Column(name = "locked_until")
    private Instant lockedUntil;
}
