package com.vetspace.repository;

import com.vetspace.domain.content.Module;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModuleRepository extends JpaRepository<Module, UUID> {

    List<Module> findByStudyYearOrderByPositionAsc(Integer studyYear);

    List<Module> findBySchoolIdAndStudyYearAndPublishedTrueOrderByPositionAsc(UUID schoolId, Integer studyYear);

    @Query("select coalesce(max(m.position), 0) from Module m where m.school.id = :schoolId and m.studyYear = :studyYear")
    int maxPosition(@Param("schoolId") UUID schoolId, @Param("studyYear") Integer studyYear);
}
