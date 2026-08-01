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
     * Name-based lookup for the bulk import, so authors can write "Parvovirose" instead of
     * a UUID. Returns a list, never an Optional: course names are unique to nothing —
     * "Ostéologie" can exist in several modules and several years — so the caller has to
     * see every match and refuse an ambiguous one rather than silently import into
     * whichever row came back first.
     *
     * <p>`moduleName` and `studyYear` are optional narrowing: null means "don't filter on
     * it", which is how one field can disambiguate without the author having to supply all
     * three. Matching is case-insensitive; the caller trims.
     *
     * <p>Two things here are load-bearing rather than style, both because a NULL parameter
     * gives Postgres nothing to infer a type from — the driver then binds `bytea` and the
     * statement dies with "function lower(bytea) does not exist", at runtime and only on
     * the null path. The `cast(...)` pins the type at the `is null` test, and
     * `moduleNameLower` is pre-lowercased by the caller so the nullable parameter never
     * sits inside a `lower()` call where it would be untyped again.
     */
    @Query("""
        select c from Course c
          join c.module m
        where lower(c.name) = lower(:courseName)
          and (cast(:moduleNameLower as string) is null or lower(m.name) = :moduleNameLower)
          and (cast(:studyYear as integer) is null or m.studyYear = :studyYear)
        order by m.studyYear asc, m.name asc, c.name asc
        """)
    List<Course> findByNameForImport(@Param("courseName") String courseName,
                                      @Param("moduleNameLower") String moduleNameLower,
                                      @Param("studyYear") Integer studyYear);

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
