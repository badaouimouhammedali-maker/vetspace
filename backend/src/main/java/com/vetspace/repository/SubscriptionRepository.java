package com.vetspace.repository;

import com.vetspace.domain.access.Subscription;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @Query("select s from Subscription s where s.user.id = :userId and :now between s.startsAt and s.endsAt")
    List<Subscription> findActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("select s from Subscription s where lower(s.user.email) = lower(:email) order by s.startsAt desc")
    List<Subscription> findByUserEmail(@Param("email") String email);

    @Query("select count(s) from Subscription s where :now between s.startsAt and s.endsAt")
    long countActive(@Param("now") Instant now);

    @Query("select count(s) from Subscription s where s.user.id = :userId and :now between s.startsAt and s.endsAt")
    long countActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * The access gate: an in-window subscription whose pack targets the student's study
     * year (null = every year). Content went national, so the school a student belongs to
     * no longer takes part — a pack bought by any student unlocks the same national
     * catalogue for their year.
     */
    @Query("""
        select count(s) > 0 from Subscription s
        where s.user.id = :userId
          and :now between s.startsAt and s.endsAt
          and (s.pack.studyYear is null or s.pack.studyYear = :studyYear)
        """)
    boolean hasActiveMatchingSubscription(@Param("userId") UUID userId,
                                           @Param("studyYear") Integer studyYear, @Param("now") Instant now);
}
