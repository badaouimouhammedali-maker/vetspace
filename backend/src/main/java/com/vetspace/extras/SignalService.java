package com.vetspace.extras;

import com.vetspace.domain.content.Question;
import com.vetspace.domain.extras.Signal;
import com.vetspace.domain.extras.SignalStatus;
import com.vetspace.domain.user.User;
import com.vetspace.extras.dto.ExtrasDtos.SignalAdminDto;
import com.vetspace.extras.dto.ExtrasDtos.SignalDto;
import com.vetspace.extras.dto.ExtrasDtos.SignalRequest;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.SignalRepository;
import com.vetspace.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Question error reports. Students file and see their own; ADMIN/TEACHER triage with a reply. */
@Service
public class SignalService {

    public static final int DAILY_LIMIT = 10;
    private static final String RATE_SCOPE = "signals";

    private final SignalRepository signalRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final DailyUserRateLimiter rateLimiter;

    public SignalService(SignalRepository signalRepository, QuestionRepository questionRepository,
                          UserRepository userRepository, DailyUserRateLimiter rateLimiter) {
        this.signalRepository = signalRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public SignalDto create(UUID userId, SignalRequest request) {
        rateLimiter.check(RATE_SCOPE, userId, DAILY_LIMIT);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        Question question = questionRepository.findById(request.questionId())
            .filter(Question::isPublished)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
        Signal signal = signalRepository.save(Signal.builder()
            .user(user)
            .question(question)
            .message(request.message())
            .status(SignalStatus.OPEN)
            .build());
        return toDto(signal);
    }

    public List<SignalDto> myList(UUID userId) {
        return signalRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDto).toList();
    }

    public Page<SignalAdminDto> adminList(SignalStatus status, Pageable pageable) {
        Page<Signal> page = status == null
            ? signalRepository.findAll(pageable)
            : signalRepository.findByStatus(status, pageable);
        return page.map(this::toAdminDto);
    }

    @Transactional
    public SignalAdminDto close(UUID signalId, SignalStatus target, String reply) {
        if (target != SignalStatus.RESOLVED && target != SignalStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid target status");
        }
        Signal signal = signalRepository.findById(signalId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Signal not found"));
        signal.setStatus(target);
        signal.setAdminReply(reply);
        return toAdminDto(signalRepository.save(signal));
    }

    private SignalDto toDto(Signal s) {
        return new SignalDto(s.getId(), s.getQuestion().getId(), s.getQuestion().getStatement(), s.getMessage(),
            s.getStatus(), s.getAdminReply(), s.getCreatedAt(), s.getUpdatedAt());
    }

    private SignalAdminDto toAdminDto(Signal s) {
        return new SignalAdminDto(s.getId(), s.getQuestion().getId(), s.getQuestion().getStatement(),
            s.getUser().getEmail(), s.getMessage(), s.getStatus(), s.getAdminReply(),
            s.getCreatedAt(), s.getUpdatedAt());
    }
}
