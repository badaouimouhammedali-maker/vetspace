package com.vetspace.domain.session;

import com.vetspace.domain.content.Question;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
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
@Table(name = "session_questions")
public class SessionQuestion {

    @EmbeddedId
    private SessionQuestionId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("sessionId")
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("questionId")
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionState state;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "seconds_spent", nullable = false)
    private Integer secondsSpent;

    /** Last answer/consult interaction; drives the weekly stats buckets. Null while UNANSWERED. */
    @Column(name = "answered_at")
    private java.time.Instant answeredAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SessionQuestion that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
