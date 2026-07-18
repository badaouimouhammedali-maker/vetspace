package com.vetspace.extras;

import com.vetspace.extras.dto.ExtrasDtos.SupportRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support")
@PreAuthorize("isAuthenticated()")
public class SupportController {

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void submit(@AuthenticationPrincipal UUID userId, @Valid @RequestBody SupportRequest request) {
        supportService.submit(userId, request);
    }
}
