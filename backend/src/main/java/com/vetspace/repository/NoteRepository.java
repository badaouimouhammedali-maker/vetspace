package com.vetspace.repository;

import com.vetspace.domain.extras.Note;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    List<Note> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    List<Note> findByUserIdAndQuestionIdOrderByUpdatedAtDesc(UUID userId, UUID questionId);

    List<Note> findByUserIdAndCourseIdOrderByUpdatedAtDesc(UUID userId, UUID courseId);

    /** Ownership-scoped lookup: a foreign note id behaves exactly like a nonexistent one. */
    Optional<Note> findByIdAndUserId(UUID id, UUID userId);

    List<Note> findByUserId(UUID userId);
}
