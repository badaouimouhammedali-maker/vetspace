package com.vetspace.extras;

import com.vetspace.catalog.dto.QuestionPlayDto;
import com.vetspace.domain.content.Proposition;
import com.vetspace.domain.content.Question;
import com.vetspace.domain.extras.Label;
import com.vetspace.domain.extras.QuestionLabel;
import com.vetspace.domain.extras.QuestionLabelId;
import com.vetspace.domain.user.User;
import com.vetspace.extras.dto.ExtrasDtos.LabelDto;
import com.vetspace.extras.dto.ExtrasDtos.LabelRequest;
import com.vetspace.repository.LabelRepository;
import com.vetspace.repository.PropositionRepository;
import com.vetspace.repository.QuestionLabelRepository;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Personal question labels. Everything is ownership-scoped: foreign label ids read as 404. */
@Service
public class LabelService {

    private final LabelRepository labelRepository;
    private final QuestionLabelRepository questionLabelRepository;
    private final QuestionRepository questionRepository;
    private final PropositionRepository propositionRepository;
    private final UserRepository userRepository;

    public LabelService(LabelRepository labelRepository, QuestionLabelRepository questionLabelRepository,
                         QuestionRepository questionRepository, PropositionRepository propositionRepository,
                         UserRepository userRepository) {
        this.labelRepository = labelRepository;
        this.questionLabelRepository = questionLabelRepository;
        this.questionRepository = questionRepository;
        this.propositionRepository = propositionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public LabelDto create(UUID userId, LabelRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        Label label = labelRepository.save(Label.builder()
            .user(user).name(request.name()).color(request.color()).build());
        return toDto(label, 0);
    }

    public List<LabelDto> list(UUID userId) {
        return labelRepository.findByUserIdOrderByNameAsc(userId).stream()
            .map(l -> toDto(l, questionLabelRepository.questionIdsForLabel(l.getId()).size()))
            .toList();
    }

    @Transactional
    public LabelDto update(UUID userId, UUID labelId, LabelRequest request) {
        Label label = requireOwned(labelId, userId);
        label.setName(request.name());
        label.setColor(request.color());
        label = labelRepository.save(label);
        return toDto(label, questionLabelRepository.questionIdsForLabel(labelId).size());
    }

    @Transactional
    public void delete(UUID userId, UUID labelId) {
        // question_labels rows go with it via DB cascade.
        labelRepository.delete(requireOwned(labelId, userId));
    }

    @Transactional
    public void attach(UUID userId, UUID labelId, UUID questionId) {
        requireOwned(labelId, userId);
        Question question = questionRepository.findById(questionId)
            .filter(Question::isPublished)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
        QuestionLabelId id = new QuestionLabelId(labelId, questionId);
        if (!questionLabelRepository.existsById(id)) {
            questionLabelRepository.save(QuestionLabel.builder()
                .id(id)
                .label(labelRepository.findById(labelId).orElseThrow())
                .question(question)
                .build());
        }
    }

    @Transactional
    public void detach(UUID userId, UUID labelId, UUID questionId) {
        requireOwned(labelId, userId);
        questionLabelRepository.deleteById(new QuestionLabelId(labelId, questionId));
    }

    /** Questions under a label as play DTOs — safe to show anywhere, never carries correction data. */
    public List<QuestionPlayDto> questions(UUID userId, UUID labelId) {
        requireOwned(labelId, userId);
        List<UUID> questionIds = questionLabelRepository.questionIdsForLabel(labelId);
        if (questionIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Proposition>> propositions = propositionRepository
            .findByQuestionIdInOrderByPositionAsc(questionIds).stream()
            .collect(Collectors.groupingBy(p -> p.getQuestion().getId()));
        return questionRepository.findAllById(questionIds).stream()
            .map(q -> new QuestionPlayDto(q.getId(), q.getCourse().getId(), q.getStatement(), q.getStatementImages(),
                q.getDifficulty(),
                propositions.getOrDefault(q.getId(), List.of()).stream()
                    .map(p -> new QuestionPlayDto.PropositionPlayDto(p.getId(), p.getLetter(), p.getText(), p.getPosition()))
                    .toList()))
            .toList();
    }

    private Label requireOwned(UUID labelId, UUID userId) {
        return labelRepository.findByIdAndUserId(labelId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Label not found"));
    }

    private LabelDto toDto(Label label, long questionCount) {
        return new LabelDto(label.getId(), label.getName(), label.getColor(), questionCount);
    }
}
