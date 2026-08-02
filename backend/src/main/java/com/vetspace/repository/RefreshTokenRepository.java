package com.vetspace.repository;

import com.vetspace.domain.user.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByFamilyIdAndRevokedAtIsNull(UUID familyId);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    /**
     * One row per LIVE family of a user, ordered least-recently-used first.
     *
     * <p>Grouping is what makes this a family list rather than a token list: rotation
     * leaves one live token per family, but a rotation racing with this read can briefly
     * leave two, and counting rows would then evict a device that was merely busy.
     * {@code max(lastUsedAt)} is the family's activity, so the ordering is stable however
     * many rows a family happens to have at that instant.
     */
    @Query("""
        select rt.familyId as familyId,
               max(coalesce(rt.lastUsedAt, rt.createdAt)) as lastUsedAt
        from RefreshToken rt
        where rt.user.id = :userId and rt.revokedAt is null
        group by rt.familyId
        order by max(coalesce(rt.lastUsedAt, rt.createdAt)) asc
        """)
    List<LiveFamily> findLiveFamilies(@Param("userId") UUID userId);

    /**
     * The anti-sharing signal, over one window: how many times the account was logged
     * into, and from how many distinct addresses.
     *
     * <p>Logins are counted as DISTINCT FAMILIES, not tokens — a family is created once
     * per login and then rotated every 15 minutes, so counting rows would report a single
     * studious afternoon as ~30 logins. Distinct IPs deliberately counts across all rows
     * including rotations: an account genuinely being passed around changes address
     * mid-family, and that is exactly the behaviour worth seeing.
     */
    @Query("""
        select count(distinct rt.familyId) as logins,
               count(distinct rt.createdIp) as distinctIps
        from RefreshToken rt
        where rt.user.id = :userId and rt.createdAt >= :since
        """)
    ActivitySummary summariseActivitySince(@Param("userId") UUID userId, @Param("since") Instant since);

    /** Most recent activity on a live family, for the admin list's "last seen on". */
    @Query("""
        select rt from RefreshToken rt
        where rt.user.id = :userId and rt.revokedAt is null
        order by coalesce(rt.lastUsedAt, rt.createdAt) desc
        limit 1
        """)
    Optional<RefreshToken> findMostRecentLive(@Param("userId") UUID userId);

    interface LiveFamily {
        UUID getFamilyId();

        Instant getLastUsedAt();
    }

    interface ActivitySummary {
        long getLogins();

        long getDistinctIps();
    }
}
