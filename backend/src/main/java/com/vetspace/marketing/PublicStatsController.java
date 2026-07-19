package com.vetspace.marketing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated marketing endpoints (permitAll in SecurityConfiguration). */
@RestController
@RequestMapping("/api/public")
public class PublicStatsController {

    private final PublicStatsService service;

    public PublicStatsController(PublicStatsService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public PublicStatsDto stats() {
        return service.stats();
    }
}
