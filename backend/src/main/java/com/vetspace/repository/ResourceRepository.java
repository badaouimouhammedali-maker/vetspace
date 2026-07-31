package com.vetspace.repository;

import com.vetspace.domain.content.Resource;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    List<Resource> findByModuleIdOrderByPositionAsc(UUID moduleId);

    List<Resource> findByModuleIdAndPublishedTrueOrderByPositionAsc(UUID moduleId);

    @Query("""
        select r from Resource r
        where r.published = true and r.module.studyYear = :studyYear
        order by r.module.position asc, r.position asc
        """)
    List<Resource> findPublishedByStudyYear(@Param("studyYear") Integer studyYear);

    @Query("select coalesce(max(r.position), 0) from Resource r where r.module.id = :moduleId")
    int maxPosition(@Param("moduleId") UUID moduleId);

    /** Study years that actually have something to show — drives the year selector. */
    @Query("""
        select distinct r.module.studyYear from Resource r
        where r.published = true
        order by r.module.studyYear asc
        """)
    List<Integer> findYearsWithPublishedResources();

    /** Admin totals per module, so R2 usage is visible where the uploads happen. */
    @Query("select coalesce(sum(r.fileSizeBytes), 0) from Resource r where r.module.id = :moduleId")
    long totalBytesForModule(@Param("moduleId") UUID moduleId);
}
