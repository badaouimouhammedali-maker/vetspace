package com.vetspace.repository;

import com.vetspace.domain.content.Mindmap;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MindmapRepository extends JpaRepository<Mindmap, UUID> {

    List<Mindmap> findByCourseIdOrderByTitleAsc(UUID courseId);

    List<Mindmap> findByCourseIdAndPublishedTrueOrderByTitleAsc(UUID courseId);

    long countByPublishedTrue();
}
