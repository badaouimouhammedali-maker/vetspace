package com.vetspace.extras;

import com.vetspace.extras.dto.ExtrasDtos.NotificationDto;
import com.vetspace.extras.dto.ExtrasDtos.NotificationRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/api/admin/notifications")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationDto broadcast(@Valid @RequestBody NotificationRequest request) {
        return notificationService.broadcast(request);
    }

    @GetMapping("/api/notifications")
    @PreAuthorize("isAuthenticated()")
    public List<NotificationDto> list(@AuthenticationPrincipal UUID userId) {
        return notificationService.list(userId);
    }

    @GetMapping("/api/notifications/unread-count")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UUID userId) {
        return Map.of("count", notificationService.unreadCount(userId));
    }

    @PostMapping("/api/notifications/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        notificationService.markRead(userId, id);
    }

    @PostMapping("/api/notifications/read-all")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal UUID userId) {
        notificationService.markAllRead(userId);
    }

    @DeleteMapping("/api/notifications/{id}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        notificationService.softDelete(userId, id);
    }
}
