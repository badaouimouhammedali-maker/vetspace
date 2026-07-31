package com.vetspace.domain.access;

import com.vetspace.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "packs")
public class Pack extends BaseEntity {

    /** Null = résidanat-style pack, not scoped to a single study year. */
    @Column(name = "study_year")
    private Integer studyYear;

    @Column(nullable = false)
    private String name;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "price_da", nullable = false)
    private Integer priceDa;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
