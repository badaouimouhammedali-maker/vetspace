package com.vetspace.repository;

import com.vetspace.domain.content.Module;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModuleRepository extends JpaRepository<Module, UUID> {

    List<Module> findByStudyYearOrderByPositionAsc(Integer studyYear);

    List<Module> findByStudyYearAndPublishedTrueOrderByPositionAsc(Integer studyYear);

    @Query("select coalesce(max(m.position), 0) from Module m where m.studyYear = :studyYear")
    int maxPosition(@Param("studyYear") Integer studyYear);

    /** Guards the (study_year, name) uniqueness with a 409 instead of a constraint-violation 500. */
    Optional<Module> findByStudyYearAndNameIgnoreCase(Integer studyYear, String name);
}
