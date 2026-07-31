package com.vetspace.catalog;

import com.vetspace.access.SubscriptionGate;
import com.vetspace.admin.dto.CourseDtos.CourseDto;
import com.vetspace.admin.dto.MindmapDtos.MindmapDto;
import com.vetspace.admin.dto.ModuleDtos.ModuleDto;
import com.vetspace.admin.dto.SchoolDtos.SchoolDto;
import com.vetspace.domain.content.Course;
import com.vetspace.domain.content.Mindmap;
import com.vetspace.domain.content.Module;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.repository.CourseRepository;
import com.vetspace.repository.MindmapRepository;
import com.vetspace.repository.ModuleRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only catalog for the app's non-admin surfaces. Content is national: students see
 * every published module for their study year, whatever école they belong to.
 * ADMIN/TEACHER additionally see unpublished content (they use these reads too).
 */
@Service
public class CatalogService {

    private final SchoolRepository schoolRepository;
    private final ModuleRepository moduleRepository;
    private final CourseRepository courseRepository;
    private final MindmapRepository mindmapRepository;
    private final com.vetspace.repository.SourceExamRepository sourceExamRepository;
    private final UserRepository userRepository;
    private final SubscriptionGate subscriptionGate;

    public CatalogService(SchoolRepository schoolRepository, ModuleRepository moduleRepository,
                           CourseRepository courseRepository, MindmapRepository mindmapRepository,
                           com.vetspace.repository.SourceExamRepository sourceExamRepository,
                           UserRepository userRepository, SubscriptionGate subscriptionGate) {
        this.schoolRepository = schoolRepository;
        this.moduleRepository = moduleRepository;
        this.courseRepository = courseRepository;
        this.mindmapRepository = mindmapRepository;
        this.sourceExamRepository = sourceExamRepository;
        this.userRepository = userRepository;
        this.subscriptionGate = subscriptionGate;
    }

    /** Public: the signup form needs the school list before any account exists. */
    public List<SchoolDto> listSchools() {
        return schoolRepository.findAll(Sort.by("name")).stream()
            .map(s -> new SchoolDto(s.getId(), s.getName(), s.getSlug()))
            .toList();
    }

    public List<ModuleDto> listModules(UUID callerId, Integer studyYear) {
        User caller = requireUser(callerId);
        List<Module> modules = isStaff(caller)
            ? moduleRepository.findByStudyYearOrderByPositionAsc(studyYear)
            : moduleRepository.findByStudyYearAndPublishedTrueOrderByPositionAsc(studyYear);
        return modules.stream()
            .map(m -> new ModuleDto(m.getId(), m.getStudyYear(), m.getName(), m.getPosition(), m.isPublished()))
            .toList();
    }

    public List<CourseDto> listCourses(UUID callerId, UUID moduleId) {
        User caller = requireUser(callerId);
        Module module = moduleRepository.findById(moduleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));

        if (isStaff(caller)) {
            return courseRepository.findByModuleIdOrderByPositionAsc(moduleId).stream().map(this::toDto).toList();
        }

        // Students: an unpublished module must be indistinguishable from a nonexistent
        // one (404, never 403). School is no longer part of the test — every published
        // module is national.
        if (!module.isPublished()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found");
        }
        return courseRepository.findByModuleIdAndPublishedTrueOrderByPositionAsc(moduleId).stream().map(this::toDto).toList();
    }

    /** Session-builder helper: the national source-exam list, newest first. */
    public List<com.vetspace.admin.dto.SourceExamDtos.SourceExamDto> listSourceExams(UUID callerId) {
        requireUser(callerId);
        return sourceExamRepository.findAllByOrderByYearDesc().stream()
            .map(e -> new com.vetspace.admin.dto.SourceExamDtos.SourceExamDto(
                e.getId(), e.getLabel(), e.getYear(), e.getExamType()))
            .toList();
    }

    /** Mindmaps are premium content: subscription-gated for students (403 SUBSCRIPTION_REQUIRED), visibility rules as for courses. */
    public List<MindmapDto> listMindmaps(UUID callerId, UUID courseId) {
        User caller = requireUser(callerId);
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        if (isStaff(caller)) {
            return mindmapRepository.findByCourseIdOrderByTitleAsc(courseId).stream().map(this::toDto).toList();
        }
        subscriptionGate.require(caller);
        if (!isVisibleToStudent(course)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }
        return mindmapRepository.findByCourseIdAndPublishedTrueOrderByTitleAsc(courseId).stream().map(this::toDto).toList();
    }

    public MindmapDto getMindmap(UUID callerId, UUID id) {
        User caller = requireUser(callerId);
        Mindmap mindmap = mindmapRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mindmap not found"));
        if (isStaff(caller)) {
            return toDto(mindmap);
        }
        subscriptionGate.require(caller);
        if (!mindmap.isPublished() || !isVisibleToStudent(mindmap.getCourse())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mindmap not found");
        }
        return toDto(mindmap);
    }

    /** Published all the way up the chain is the whole visibility rule now. */
    private boolean isVisibleToStudent(Course course) {
        return course.isPublished() && course.getModule().isPublished();
    }

    private MindmapDto toDto(Mindmap m) {
        return new MindmapDto(m.getId(), m.getCourse().getId(), m.getTitle(), m.getImageUrl(), m.isPublished(),
            m.getCreatedAt(), m.getUpdatedAt());
    }

    private CourseDto toDto(Course c) {
        return new CourseDto(c.getId(), c.getModule().getId(), c.getName(), c.getPosition(), c.isPublished(), c.isFreePreview());
    }

    private boolean isStaff(User user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.TEACHER;
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
