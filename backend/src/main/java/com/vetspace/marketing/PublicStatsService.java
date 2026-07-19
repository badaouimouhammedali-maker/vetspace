package com.vetspace.marketing;

import com.vetspace.repository.MindmapRepository;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.SourceExamRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregate totals for the public marketing page. Counts are cheap but this is an unauthenticated,
 * cacheable endpoint, so the result is memoized for 60s to shield the DB from landing-page traffic.
 */
@Service
public class PublicStatsService {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final QuestionRepository questionRepository;
    private final SourceExamRepository sourceExamRepository;
    private final MindmapRepository mindmapRepository;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public PublicStatsService(QuestionRepository questionRepository, SourceExamRepository sourceExamRepository,
                               MindmapRepository mindmapRepository) {
        this.questionRepository = questionRepository;
        this.sourceExamRepository = sourceExamRepository;
        this.mindmapRepository = mindmapRepository;
    }

    @Transactional(readOnly = true)
    public PublicStatsDto stats() {
        Instant now = Instant.now();
        Cached current = cache.get();
        if (current != null && current.expiresAt().isAfter(now)) {
            return current.value();
        }
        PublicStatsDto fresh = new PublicStatsDto(
            questionRepository.countByPublishedTrue(),
            sourceExamRepository.count(),
            mindmapRepository.countByPublishedTrue());
        cache.set(new Cached(fresh, now.plus(TTL)));
        return fresh;
    }

    private record Cached(PublicStatsDto value, Instant expiresAt) {
    }
}
