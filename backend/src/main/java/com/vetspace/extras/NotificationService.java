package com.vetspace.extras;

import com.vetspace.domain.extras.Notification;
import com.vetspace.domain.extras.NotificationRead;
import com.vetspace.domain.extras.NotificationReadId;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.extras.dto.ExtrasDtos.NotificationDto;
import com.vetspace.extras.dto.ExtrasDtos.NotificationRequest;
import com.vetspace.repository.NotificationReadRepository;
import com.vetspace.repository.NotificationRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Announcements. Targeting is (school, studyYear), both nullable = broadcast. Read/soft-delete
 * state lives per user in notification_reads: any row means read; deleted rows leave the list.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationReadRepository notificationReadRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                                NotificationReadRepository notificationReadRepository,
                                SchoolRepository schoolRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationReadRepository = notificationReadRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public NotificationDto broadcast(NotificationRequest request) {
        Notification notification = Notification.builder()
            .kind(request.kind())
            .title(request.title())
            .body(request.body())
            .school(request.schoolId() == null ? null
                : schoolRepository.findById(request.schoolId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "School not found")))
            .studyYear(request.studyYear())
            .build();
        return toDto(notificationRepository.save(notification), false);
    }

    public List<NotificationDto> list(UUID userId) {
        User user = requireUser(userId);
        Map<UUID, NotificationRead> reads = readsByNotification(userId);
        return targetedFor(user).stream()
            .filter(n -> {
                NotificationRead read = reads.get(n.getId());
                return read == null || !read.isDeleted();
            })
            .map(n -> toDto(n, reads.containsKey(n.getId())))
            .toList();
    }

    public long unreadCount(UUID userId) {
        User user = requireUser(userId);
        Set<UUID> readIds = readsByNotification(userId).keySet();
        return targetedFor(user).stream().filter(n -> !readIds.contains(n.getId())).count();
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        User user = requireUser(userId);
        Notification notification = targetedFor(user).stream()
            .filter(n -> n.getId().equals(notificationId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        upsertRead(user, notification, false);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        User user = requireUser(userId);
        Map<UUID, NotificationRead> reads = readsByNotification(userId);
        targetedFor(user).stream()
            .filter(n -> !reads.containsKey(n.getId()))
            .forEach(n -> upsertRead(user, n, false));
    }

    /** Soft delete: the row stays in notification_reads with deleted=true; the notification disappears from this user's list. */
    @Transactional
    public void softDelete(UUID userId, UUID notificationId) {
        User user = requireUser(userId);
        Notification notification = targetedFor(user).stream()
            .filter(n -> n.getId().equals(notificationId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        upsertRead(user, notification, true);
    }

    private void upsertRead(User user, Notification notification, boolean deleted) {
        NotificationReadId id = new NotificationReadId(user.getId(), notification.getId());
        NotificationRead read = notificationReadRepository.findById(id)
            .orElseGet(() -> NotificationRead.builder()
                .id(id).user(user).notification(notification).readAt(Instant.now()).build());
        if (deleted) {
            read.setDeleted(true);
        }
        notificationReadRepository.save(read);
    }

    private List<Notification> targetedFor(User user) {
        boolean staff = user.getRole() == Role.ADMIN || user.getRole() == Role.TEACHER;
        if (staff || user.getSchool() == null) {
            return notificationRepository.findAllByOrderByCreatedAtDesc();
        }
        return notificationRepository.findTargeted(user.getSchool().getId(), user.getStudyYear());
    }

    private Map<UUID, NotificationRead> readsByNotification(UUID userId) {
        return notificationReadRepository.findAllForUser(userId).stream()
            .collect(Collectors.toMap(r -> r.getId().getNotificationId(), Function.identity()));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }

    private NotificationDto toDto(Notification n, boolean read) {
        return new NotificationDto(n.getId(), n.getKind(), n.getTitle(), n.getBody(), n.getCreatedAt(), read);
    }
}
