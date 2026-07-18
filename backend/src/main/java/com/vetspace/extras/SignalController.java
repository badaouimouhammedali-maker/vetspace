package com.vetspace.extras;

import com.vetspace.domain.extras.SignalStatus;
import com.vetspace.extras.dto.ExtrasDtos.SignalAdminDto;
import com.vetspace.extras.dto.ExtrasDtos.SignalDto;
import com.vetspace.extras.dto.ExtrasDtos.SignalReplyRequest;
import com.vetspace.extras.dto.ExtrasDtos.SignalRequest;
import com.vetspace.web.PageResponse;
import com.vetspace.web.Paging;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SignalController {

    private final SignalService signalService;

    public SignalController(SignalService signalService) {
        this.signalService = signalService;
    }

    @PostMapping("/api/signals")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public SignalDto create(@AuthenticationPrincipal UUID userId, @Valid @RequestBody SignalRequest request) {
        return signalService.create(userId, request);
    }

    @GetMapping("/api/signals")
    @PreAuthorize("isAuthenticated()")
    public List<SignalDto> myList(@AuthenticationPrincipal UUID userId) {
        return signalService.myList(userId);
    }

    @GetMapping("/api/admin/signals")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public PageResponse<SignalAdminDto> adminList(@RequestParam(required = false) SignalStatus status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(signalService.adminList(status, Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @PostMapping("/api/admin/signals/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public SignalAdminDto resolve(@PathVariable UUID id, @Valid @RequestBody SignalReplyRequest request) {
        return signalService.close(id, SignalStatus.RESOLVED, request.reply());
    }

    @PostMapping("/api/admin/signals/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public SignalAdminDto reject(@PathVariable UUID id, @Valid @RequestBody SignalReplyRequest request) {
        return signalService.close(id, SignalStatus.REJECTED, request.reply());
    }
}
