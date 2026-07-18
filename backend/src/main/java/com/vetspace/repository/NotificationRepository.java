package com.vetspace.repository;

import com.vetspace.domain.extras.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Everything targeted at this school+studyYear, including broadcasts (null targeting matches everyone). */
    @Query("""
        select n from Notification n
        where (n.school is null or n.school.id = :schoolId)
          and (n.studyYear is null or n.studyYear = :studyYear)
        order by n.createdAt desc
        """)
    List<Notification> findTargeted(@Param("schoolId") UUID schoolId, @Param("studyYear") Integer studyYear);

    List<Notification> findAllByOrderByCreatedAtDesc();
}
