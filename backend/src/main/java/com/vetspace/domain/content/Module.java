package com.vetspace.domain.content;

import com.vetspace.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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
@Table(name = "modules")
public class Module extends BaseEntity {

    @Column(name = "study_year", nullable = false)
    private Integer studyYear;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false)
    private boolean published;
}
