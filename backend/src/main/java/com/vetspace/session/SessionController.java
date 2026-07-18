package com.vetspace.session;

import com.vetspace.session.dto.SessionDtos.AnswerRequest;
import com.vetspace.session.dto.SessionDtos.CorrectionDto;
import com.vetspace.session.dto.SessionDtos.CreateSessionRequest;
import com.vetspace.session.dto.SessionDtos.RepeatRequest;
import com.vetspace.session.dto.SessionDtos.SessionPlayDto;
import com.vetspace.session.dto.SessionDtos.SessionSummaryDto;
import com.vetspace.session.dto.SessionDtos.UpdateSessionRequest;
import com.vetspace.web.PageResponse;
import com.vetspace.web.Paging;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
@PreAuthorize("isAuthenticated()")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionSummaryDto create(@AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateSessionRequest request) {
        return sessionService.create(userId, request);
    }

    /** My sessions: favorites first, then most recent — mirrors the reference UI's ordering. */
    @GetMapping
    public PageResponse<SessionSummaryDto> list(@AuthenticationPrincipal UUID userId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        Sort sort = Sort.by(Sort.Order.desc("favorite"), Sort.Order.desc("startedAt"));
        return PageResponse.of(sessionService.list(userId, Paging.of(page, size, sort)));
    }

    @GetMapping("/{id}/play")
    public SessionPlayDto play(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return sessionService.play(userId, id);
    }

    @PutMapping("/{id}/questions/{qid}/answer")
    public CorrectionDto answer(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                                 @PathVariable UUID qid, @Valid @RequestBody AnswerRequest request) {
        return sessionService.answer(userId, id, qid, request);
    }

    @PostMapping("/{id}/questions/{qid}/consult")
    public CorrectionDto consult(@AuthenticationPrincipal UUID userId, @PathVariable UUID id, @PathVariable UUID qid) {
        return sessionService.consult(userId, id, qid);
    }

    @PostMapping("/{id}/submit")
    public SessionSummaryDto submit(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return sessionService.submit(userId, id);
    }

    @PatchMapping("/{id}")
    public SessionSummaryDto patch(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                                    @Valid @RequestBody UpdateSessionRequest request) {
        return sessionService.patch(userId, id, request);
    }

    @PostMapping("/{id}/repeat")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionSummaryDto repeat(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                                     @Valid @RequestBody RepeatRequest request) {
        return sessionService.repeat(userId, id, request.mode());
    }

    @PostMapping("/{id}/reset")
    public SessionPlayDto reset(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return sessionService.reset(userId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        sessionService.delete(userId, id);
    }
}
