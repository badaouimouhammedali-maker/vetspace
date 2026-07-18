package com.vetspace.repository;

import com.vetspace.domain.content.Course;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findByModuleIdOrderByPositionAsc(UUID moduleId);

    List<Course> findByModuleIdAndPublishedTrueOrderByPositionAsc(UUID moduleId);

    @Query("select coalesce(max(c.position), 0) from Course c where c.module.id = :moduleId")
    int maxPosition(@Param("moduleId") UUID moduleId);
}
