package com.vetspace.repository;

import com.vetspace.domain.extras.Label;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabelRepository extends JpaRepository<Label, UUID> {
}
