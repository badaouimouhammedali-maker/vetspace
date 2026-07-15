package com.vetspace.domain.content;

import com.vetspace.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "propositions")
public class Proposition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false, length = 1)
    private String letter;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "is_true", nullable = false)
    private boolean isTrue;

    @Column(name = "explanation_html", columnDefinition = "text")
    private String explanationHtml;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanation_images", nullable = false, columnDefinition = "jsonb")
    private List<String> explanationImages = new ArrayList<>();

    @Column(nullable = false)
    private Integer position;
}
