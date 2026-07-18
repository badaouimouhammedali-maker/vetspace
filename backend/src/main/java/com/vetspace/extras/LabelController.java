package com.vetspace.extras;

import com.vetspace.catalog.dto.QuestionPlayDto;
import com.vetspace.extras.dto.ExtrasDtos.LabelDto;
import com.vetspace.extras.dto.ExtrasDtos.LabelRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labels")
@PreAuthorize("isAuthenticated()")
public class LabelController {

    private final LabelService labelService;

    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LabelDto create(@AuthenticationPrincipal UUID userId, @Valid @RequestBody LabelRequest request) {
        return labelService.create(userId, request);
    }

    @GetMapping
    public List<LabelDto> list(@AuthenticationPrincipal UUID userId) {
        return labelService.list(userId);
    }

    @PutMapping("/{id}")
    public LabelDto update(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                            @Valid @RequestBody LabelRequest request) {
        return labelService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        labelService.delete(userId, id);
    }

    @PostMapping("/{id}/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void attach(@AuthenticationPrincipal UUID userId, @PathVariable UUID id, @PathVariable UUID questionId) {
        labelService.attach(userId, id, questionId);
    }

    @DeleteMapping("/{id}/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void detach(@AuthenticationPrincipal UUID userId, @PathVariable UUID id, @PathVariable UUID questionId) {
        labelService.detach(userId, id, questionId);
    }

    @GetMapping("/{id}/questions")
    public List<QuestionPlayDto> questions(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return labelService.questions(userId, id);
    }
}
