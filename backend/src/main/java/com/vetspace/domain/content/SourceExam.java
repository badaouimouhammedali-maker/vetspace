package com.vetspace.domain.content;

import com.vetspace.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "source_exams")
public class SourceExam extends BaseEntity {

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 20)
    private ExamType examType;
}
