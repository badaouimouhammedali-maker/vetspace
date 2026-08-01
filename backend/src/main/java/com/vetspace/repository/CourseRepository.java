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

    /**
     * The published catalogue of one study year, with each course's published-question
     * count — the denominators of the coverage screen.
     *
     * <p>Deliberately independent of any user, so it stays a plain catalogue read that
     * every student of the year gets the same answer to. The LEFT JOIN is what keeps a
     * course with no questions in the list, reporting 0, instead of vanishing from a
     * screen whose whole job is showing what has not been covered.
     */
    @Query("""
        select c.id as courseId,
               c.name as courseName,
               c.position as coursePosition,
               m.id as moduleId,
               m.name as moduleName,
               m.position as modulePosition,
               count(q.id) as totalQuestions
        from Course c
          join c.module m
          left join Question q on q.course = c and q.published = true
        where m.studyYear = :studyYear
          and m.published = true
          and c.published = true
        group by c.id, c.name, c.position, m.id, m.name, m.position
        order by m.position asc, m.name asc, c.position asc, c.name asc
        """)
    List<CourseCatalogRow> publishedCatalogForYear(@Param("studyYear") Integer studyYear);

    interface CourseCatalogRow {
        UUID getCourseId();

        String getCourseName();

        int getCoursePosition();

        UUID getModuleId();

        String getModuleName();

        int getModulePosition();

        long getTotalQuestions();
    }
}
