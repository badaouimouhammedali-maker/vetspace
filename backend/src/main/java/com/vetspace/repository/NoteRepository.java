package com.vetspace.repository;

import com.vetspace.domain.extras.Note;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, UUID> {
}
