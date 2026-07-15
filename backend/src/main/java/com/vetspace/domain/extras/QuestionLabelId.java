package com.vetspace.domain.extras;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class QuestionLabelId implements Serializable {

    @Column(name = "label_id", columnDefinition = "uuid")
    private UUID labelId;

    @Column(name = "question_id", columnDefinition = "uuid")
    private UUID questionId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QuestionLabelId that)) {
            return false;
        }
        return Objects.equals(labelId, that.labelId) && Objects.equals(questionId, that.questionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labelId, questionId);
    }
}
