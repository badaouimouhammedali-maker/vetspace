package com.vetspace.access;

import com.vetspace.access.dto.AccessDtos.PublicPackDto;
import com.vetspace.access.dto.AccessDtos.RedeemRequest;
import com.vetspace.access.dto.AccessDtos.RedeemResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AccessController {

    private final RedemptionService redemptionService;
    private final RedeemRateLimiter redeemRateLimiter;

    public AccessController(RedemptionService redemptionService, RedeemRateLimiter redeemRateLimiter) {
        this.redemptionService = redemptionService;
        this.redeemRateLimiter = redeemRateLimiter;
    }

    /** Public: the marketing/pricing page reads this without an account. */
    @GetMapping("/packs")
    public List<PublicPackDto> packs(@RequestParam(required = false) UUID schoolId,
                                      @RequestParam(required = false) Integer studyYear) {
        return redemptionService.publicPacks(schoolId, studyYear);
    }

    @PostMapping("/codes/redeem")
    @PreAuthorize("isAuthenticated()")
    public RedeemResponse redeem(@AuthenticationPrincipal UUID userId, @Valid @RequestBody RedeemRequest request,
                                  HttpServletRequest httpRequest) {
        redeemRateLimiter.check(userId, clientIp(httpRequest));
        return redemptionService.redeem(userId, request.code());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
