package com.vetspace.extras;

import com.vetspace.extras.dto.ExtrasDtos.NoteDto;
import com.vetspace.extras.dto.ExtrasDtos.NoteRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notes")
@PreAuthorize("isAuthenticated()")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteDto create(@AuthenticationPrincipal UUID userId, @Valid @RequestBody NoteRequest request) {
        return noteService.create(userId, request);
    }

    @GetMapping
    public List<NoteDto> list(@AuthenticationPrincipal UUID userId,
                               @RequestParam(required = false) UUID questionId,
                               @RequestParam(required = false) UUID courseId) {
        return noteService.list(userId, questionId, courseId);
    }

    @GetMapping("/{id}")
    public NoteDto get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return noteService.get(userId, id);
    }

    @PutMapping("/{id}")
    public NoteDto update(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                           @Valid @RequestBody NoteRequest request) {
        return noteService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        noteService.delete(userId, id);
    }
}
