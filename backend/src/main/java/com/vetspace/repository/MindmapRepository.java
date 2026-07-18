package com.vetspace.repository;

import com.vetspace.domain.content.Mindmap;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MindmapRepository extends JpaRepository<Mindmap, UUID> {

    List<Mindmap> findByCourseIdOrderByTitleAsc(UUID courseId);

    List<Mindmap> findByCourseIdAndPublishedTrueOrderByTitleAsc(UUID courseId);

    long countByPublishedTrue();

    @Query("select count(m) from Mindmap m where m.published = true and m.course.module.school.id = :schoolId")
    long countPublishedForSchool(@Param("schoolId") UUID schoolId);
}
