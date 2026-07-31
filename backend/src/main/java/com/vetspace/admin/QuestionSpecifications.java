package com.vetspace.admin;

import com.vetspace.domain.content.Difficulty;
import com.vetspace.domain.content.Question;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Composable filters shared by the admin question list and the student-facing count. */
public final class QuestionSpecifications {

    private QuestionSpecifications() {
    }

    public static Specification<Question> courseId(UUID courseId) {
        return courseId == null ? null : (root, query, cb) -> cb.equal(root.get("course").get("id"), courseId);
    }

    public static Specification<Question> courseIds(Collection<UUID> courseIds) {
        return courseIds == null || courseIds.isEmpty() ? null
            : (root, query, cb) -> root.get("course").get("id").in(courseIds);
    }

    public static Specification<Question> moduleId(UUID moduleId) {
        return moduleId == null ? null : (root, query, cb) -> cb.equal(root.get("course").get("module").get("id"), moduleId);
    }

    public static Specification<Question> sourceExamId(UUID sourceExamId) {
        return sourceExamId == null ? null : (root, query, cb) -> cb.equal(root.get("sourceExam").get("id"), sourceExamId);
    }

    public static Specification<Question> difficulty(Difficulty difficulty) {
        return difficulty == null ? null : (root, query, cb) -> cb.equal(root.get("difficulty"), difficulty);
    }

    public static Specification<Question> published(Boolean published) {
        return published == null ? null : (root, query, cb) -> cb.equal(root.get("published"), published);
    }

    public static Specification<Question> statementContains(String q) {
        return q == null || q.isBlank() ? null
            : (root, query, cb) -> cb.like(cb.lower(root.get("statement")), "%" + q.toLowerCase() + "%");
    }

    /** Questions carrying at least one of the given labels — restricted to the owner's labels so ids can't probe other users' data. */
    public static Specification<Question> hasAnyLabel(Collection<UUID> labelIds, UUID ownerId) {
        return labelIds == null || labelIds.isEmpty() ? null : (root, query, cb) -> {
            var sub = query.subquery(UUID.class);
            var ql = sub.from(com.vetspace.domain.extras.QuestionLabel.class);
            sub.select(ql.get("question").get("id"))
                .where(cb.and(
                    ql.get("label").get("id").in(labelIds),
                    cb.equal(ql.get("label").get("user").get("id"), ownerId)));
            return root.get("id").in(sub);
        };
    }

    /**
     * Everything a student may see/count: a published question in a published course in a
     * published module. Content is national, so published is the whole test — there is no
     * school leg any more.
     */
    public static Specification<Question> visibleToStudent() {
        return (root, query, cb) -> cb.and(
            cb.isTrue(root.get("published")),
            cb.isTrue(root.get("course").get("published")),
            cb.isTrue(root.get("course").get("module").get("published")));
    }
}
