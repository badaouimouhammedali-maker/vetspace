package com.vetspace.domain.session;

import com.vetspace.domain.common.BaseEntity;
import com.vetspace.domain.content.Proposition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
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
@Table(name = "session_answers")
public class SessionAnswer extends BaseEntity {

    /** One row per selected proposition, scoped to a (session_id, question_id) session_questions row. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "session_id", referencedColumnName = "session_id", nullable = false),
        @JoinColumn(name = "question_id", referencedColumnName = "question_id", nullable = false)
    })
    private SessionQuestion sessionQuestion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposition_id", nullable = false)
    private Proposition proposition;

    @Column(nullable = false)
    private boolean selected;
}
