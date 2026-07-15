package com.vetspace.repository;

import com.vetspace.domain.extras.NotificationRead;
import com.vetspace.domain.extras.NotificationReadId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, NotificationReadId> {
}
