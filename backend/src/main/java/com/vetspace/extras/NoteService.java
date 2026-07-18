package com.vetspace.extras;

import com.vetspace.domain.extras.Note;
import com.vetspace.domain.user.User;
import com.vetspace.extras.dto.ExtrasDtos.NoteDto;
import com.vetspace.extras.dto.ExtrasDtos.NoteRequest;
import com.vetspace.repository.CourseRepository;
import com.vetspace.repository.NoteRepository;
import com.vetspace.repository.QuestionRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.HtmlSanitizer;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Personal notes; content_html always passes through the sanitizer on save. Foreign note ids read as 404. */
@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final QuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final HtmlSanitizer htmlSanitizer;

    public NoteService(NoteRepository noteRepository, QuestionRepository questionRepository,
                        CourseRepository courseRepository, UserRepository userRepository, HtmlSanitizer htmlSanitizer) {
        this.noteRepository = noteRepository;
        this.questionRepository = questionRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.htmlSanitizer = htmlSanitizer;
    }

    @Transactional
    public NoteDto create(UUID userId, NoteRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        Note note = Note.builder()
            .user(user)
            .question(resolveQuestion(request.questionId()))
            .course(resolveCourse(request.courseId()))
            .title(request.title())
            .contentHtml(htmlSanitizer.sanitize(request.contentHtml()))
            .build();
        return toDto(noteRepository.save(note));
    }

    public List<NoteDto> list(UUID userId, UUID questionId, UUID courseId) {
        List<Note> notes;
        if (questionId != null) {
            notes = noteRepository.findByUserIdAndQuestionIdOrderByUpdatedAtDesc(userId, questionId);
        } else if (courseId != null) {
            notes = noteRepository.findByUserIdAndCourseIdOrderByUpdatedAtDesc(userId, courseId);
        } else {
            notes = noteRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        }
        return notes.stream().map(this::toDto).toList();
    }

    public NoteDto get(UUID userId, UUID noteId) {
        return toDto(requireOwned(noteId, userId));
    }

    @Transactional
    public NoteDto update(UUID userId, UUID noteId, NoteRequest request) {
        Note note = requireOwned(noteId, userId);
        note.setTitle(request.title());
        note.setContentHtml(htmlSanitizer.sanitize(request.contentHtml()));
        note.setQuestion(resolveQuestion(request.questionId()));
        note.setCourse(resolveCourse(request.courseId()));
        return toDto(noteRepository.save(note));
    }

    @Transactional
    public void delete(UUID userId, UUID noteId) {
        noteRepository.delete(requireOwned(noteId, userId));
    }

    private com.vetspace.domain.content.Question resolveQuestion(UUID id) {
        if (id == null) {
            return null;
        }
        return questionRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
    }

    private com.vetspace.domain.content.Course resolveCourse(UUID id) {
        if (id == null) {
            return null;
        }
        return courseRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    }

    private Note requireOwned(UUID noteId, UUID userId) {
        return noteRepository.findByIdAndUserId(noteId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
    }

    private NoteDto toDto(Note n) {
        return new NoteDto(n.getId(), n.getTitle(), n.getContentHtml(),
            n.getQuestion() != null ? n.getQuestion().getId() : null,
            n.getCourse() != null ? n.getCourse().getId() : null,
            n.getCreatedAt(), n.getUpdatedAt());
    }
}
