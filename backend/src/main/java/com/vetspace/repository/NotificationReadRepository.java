package com.vetspace.repository;

import com.vetspace.domain.extras.NotificationRead;
import com.vetspace.domain.extras.NotificationReadId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, NotificationReadId> {

    @Query("select nr from NotificationRead nr where nr.id.userId = :userId")
    List<NotificationRead> findAllForUser(@Param("userId") UUID userId);
}
