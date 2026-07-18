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

    /** The access gate: an in-window subscription whose pack targets the student's school and study year (null = any year). */
    @Query("""
        select count(s) > 0 from Subscription s
        where s.user.id = :userId
          and :now between s.startsAt and s.endsAt
          and s.pack.school.id = :schoolId
          and (s.pack.studyYear is null or s.pack.studyYear = :studyYear)
        """)
    boolean hasActiveMatchingSubscription(@Param("userId") UUID userId, @Param("schoolId") UUID schoolId,
                                           @Param("studyYear") Integer studyYear, @Param("now") Instant now);
}
