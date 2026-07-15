package com.vetspace.repository;

import com.vetspace.domain.content.Mindmap;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MindmapRepository extends JpaRepository<Mindmap, UUID> {
}
