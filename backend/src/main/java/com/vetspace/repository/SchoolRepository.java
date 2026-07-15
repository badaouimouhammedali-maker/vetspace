package com.vetspace.repository;

import com.vetspace.domain.school.School;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolRepository extends JpaRepository<School, UUID> {

    Optional<School> findBySlug(String slug);
}
