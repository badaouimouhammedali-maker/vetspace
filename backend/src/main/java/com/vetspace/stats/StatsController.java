package com.vetspace.stats;

import com.vetspace.domain.session.SessionType;
import com.vetspace.stats.dto.StatsDtos.CourseStatsDto;
import com.vetspace.stats.dto.StatsDtos.DailyStatsDto;
import com.vetspace.stats.dto.StatsDtos.OverviewDto;
import com.vetspace.stats.dto.StatsDtos.SessionStatsDto;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@PreAuthorize("isAuthenticated()")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/sessions")
    public List<SessionStatsDto> sessions(@AuthenticationPrincipal UUID userId,
                                           @RequestParam(required = false) SessionType type) {
        return statsService.sessionStats(userId, type);
    }

    @GetMapping("/sessions/{id}/by-course")
    public List<CourseStatsDto> byCourse(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return statsService.byCourse(userId, id);
    }

    @GetMapping("/weekly")
    public List<DailyStatsDto> weekly(@AuthenticationPrincipal UUID userId) {
        return statsService.weekly(userId);
    }

    @GetMapping("/overview")
    public OverviewDto overview(@AuthenticationPrincipal UUID userId) {
        return statsService.overview(userId);
    }
}
