package com.vetspace.admin;

import com.vetspace.admin.dto.CourseDtos.CourseDto;
import com.vetspace.admin.dto.CourseDtos.CourseRequest;
import com.vetspace.admin.dto.MindmapDtos.MindmapDto;
import com.vetspace.admin.dto.MindmapDtos.MindmapRequest;
import com.vetspace.admin.dto.ModuleDtos.ModuleDto;
import com.vetspace.admin.dto.ModuleDtos.ModuleRequest;
import com.vetspace.admin.dto.SchoolDtos.SchoolDto;
import com.vetspace.admin.dto.SchoolDtos.SchoolRequest;
import com.vetspace.admin.dto.SourceExamDtos.SourceExamDto;
import com.vetspace.admin.dto.SourceExamDtos.SourceExamRequest;
import com.vetspace.domain.content.Course;
import com.vetspace.domain.content.Mindmap;
import com.vetspace.domain.content.Module;
import com.vetspace.domain.content.SourceExam;
import com.vetspace.domain.school.School;
import com.vetspace.repository.CourseRepository;
import com.vetspace.repository.MindmapRepository;
import com.vetspace.repository.ModuleRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.SourceExamRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminCatalogService {

    private final SchoolRepository schoolRepository;
    private final ModuleRepository moduleRepository;
    private final CourseRepository courseRepository;
    private final SourceExamRepository sourceExamRepository;
    private final MindmapRepository mindmapRepository;

    public AdminCatalogService(SchoolRepository schoolRepository, ModuleRepository moduleRepository,
                                CourseRepository courseRepository, SourceExamRepository sourceExamRepository,
                                MindmapRepository mindmapRepository) {
        this.schoolRepository = schoolRepository;
        this.moduleRepository = moduleRepository;
        this.courseRepository = courseRepository;
        this.sourceExamRepository = sourceExamRepository;
        this.mindmapRepository = mindmapRepository;
    }

    // ---------------------------------------------------------------
    // Schools
    // ---------------------------------------------------------------

    @Transactional
    public SchoolDto createSchool(SchoolRequest request) {
        School school = schoolRepository.save(School.builder().name(request.name()).slug(request.slug()).build());
        return toDto(school);
    }

    public Page<SchoolDto> listSchools(Pageable pageable) {
        return schoolRepository.findAll(pageable).map(this::toDto);
    }

    public SchoolDto getSchool(UUID id) {
        return toDto(requireSchool(id));
    }

    @Transactional
    public SchoolDto updateSchool(UUID id, SchoolRequest request) {
        School school = requireSchool(id);
        school.setName(request.name());
        school.setSlug(request.slug());
        return toDto(schoolRepository.save(school));
    }

    @Transactional
    public void deleteSchool(UUID id) {
        schoolRepository.delete(requireSchool(id));
    }

    // ---------------------------------------------------------------
    // Modules
    // ---------------------------------------------------------------

    @Transactional
    public ModuleDto createModule(ModuleRequest request) {
        requireNameFreeForYear(request.studyYear(), request.name(), null);
        Module module = Module.builder()
            .studyYear(request.studyYear())
            .name(request.name())
            .position(moduleRepository.maxPosition(request.studyYear()) + 1)
            .published(request.published())
            .build();
        return toDto(moduleRepository.save(module));
    }

    public Page<ModuleDto> listModules(Pageable pageable) {
        return moduleRepository.findAll(pageable).map(this::toDto);
    }

    public ModuleDto getModule(UUID id) {
        return toDto(requireModule(id));
    }

    @Transactional
    public ModuleDto updateModule(UUID id, ModuleRequest request) {
        Module module = requireModule(id);
        requireNameFreeForYear(request.studyYear(), request.name(), id);
        module.setStudyYear(request.studyYear());
        module.setName(request.name());
        module.setPublished(request.published());
        return toDto(moduleRepository.save(module));
    }

    @Transactional
    public void deleteModule(UUID id) {
        moduleRepository.delete(requireModule(id));
    }

    @Transactional
    public ModuleDto setModulePublished(UUID id, boolean published) {
        Module module = requireModule(id);
        module.setPublished(published);
        return toDto(moduleRepository.save(module));
    }

    /** Rewrites positions to match the given order. Every id must exist; the list must cover whole rows being reordered. */
    @Transactional
    public List<ModuleDto> reorderModules(List<UUID> orderedIds) {
        List<Module> modules = loadAllOrThrow(orderedIds, moduleRepository::findAllById, "module");
        applyOrder(orderedIds, modules, (m, pos) -> m.setPosition(pos));
        return moduleRepository.saveAll(modules).stream().map(this::toDto).toList();
    }

    // ---------------------------------------------------------------
    // Courses
    // ---------------------------------------------------------------

    @Transactional
    public CourseDto createCourse(CourseRequest request) {
        Module module = requireModule(request.moduleId());
        Course course = Course.builder()
            .module(module)
            .name(request.name())
            .position(courseRepository.maxPosition(module.getId()) + 1)
            .published(request.published())
            .freePreview(request.freePreview())
            .build();
        return toDto(courseRepository.save(course));
    }

    public Page<CourseDto> listCourses(Pageable pageable) {
        return courseRepository.findAll(pageable).map(this::toDto);
    }

    public CourseDto getCourse(UUID id) {
        return toDto(requireCourse(id));
    }

    @Transactional
    public CourseDto updateCourse(UUID id, CourseRequest request) {
        Course course = requireCourse(id);
        course.setModule(requireModule(request.moduleId()));
        course.setName(request.name());
        course.setPublished(request.published());
        course.setFreePreview(request.freePreview());
        return toDto(courseRepository.save(course));
    }

    @Transactional
    public void deleteCourse(UUID id) {
        courseRepository.delete(requireCourse(id));
    }

    @Transactional
    public CourseDto setCoursePublished(UUID id, boolean published) {
        Course course = requireCourse(id);
        course.setPublished(published);
        return toDto(courseRepository.save(course));
    }

    @Transactional
    public List<CourseDto> reorderCourses(List<UUID> orderedIds) {
        List<Course> courses = loadAllOrThrow(orderedIds, courseRepository::findAllById, "course");
        applyOrder(orderedIds, courses, (c, pos) -> c.setPosition(pos));
        return courseRepository.saveAll(courses).stream().map(this::toDto).toList();
    }

    // ---------------------------------------------------------------
    // Source exams
    // ---------------------------------------------------------------

    @Transactional
    public SourceExamDto createSourceExam(SourceExamRequest request) {
        SourceExam exam = SourceExam.builder()
            .label(request.label())
            .year(request.year())
            .examType(request.examType())
            .build();
        return toDto(sourceExamRepository.save(exam));
    }

    public Page<SourceExamDto> listSourceExams(Pageable pageable) {
        return sourceExamRepository.findAll(pageable).map(this::toDto);
    }

    public SourceExamDto getSourceExam(UUID id) {
        return toDto(requireSourceExam(id));
    }

    @Transactional
    public SourceExamDto updateSourceExam(UUID id, SourceExamRequest request) {
        SourceExam exam = requireSourceExam(id);
        exam.setLabel(request.label());
        exam.setYear(request.year());
        exam.setExamType(request.examType());
        return toDto(sourceExamRepository.save(exam));
    }

    @Transactional
    public void deleteSourceExam(UUID id) {
        sourceExamRepository.delete(requireSourceExam(id));
    }

    // ---------------------------------------------------------------
    // Mindmaps
    // ---------------------------------------------------------------

    @Transactional
    public MindmapDto createMindmap(MindmapRequest request) {
        Mindmap mindmap = Mindmap.builder()
            .course(requireCourse(request.courseId()))
            .title(request.title())
            .imageUrl(request.imageUrl())
            .published(request.published())
            .build();
        return toDto(mindmapRepository.save(mindmap));
    }

    public Page<MindmapDto> listMindmaps(Pageable pageable) {
        return mindmapRepository.findAll(pageable).map(this::toDto);
    }

    public MindmapDto getMindmap(UUID id) {
        return toDto(requireMindmap(id));
    }

    @Transactional
    public MindmapDto updateMindmap(UUID id, MindmapRequest request) {
        Mindmap mindmap = requireMindmap(id);
        mindmap.setCourse(requireCourse(request.courseId()));
        mindmap.setTitle(request.title());
        mindmap.setImageUrl(request.imageUrl());
        mindmap.setPublished(request.published());
        return toDto(mindmapRepository.save(mindmap));
    }

    @Transactional
    public void deleteMindmap(UUID id) {
        mindmapRepository.delete(requireMindmap(id));
    }

    @Transactional
    public MindmapDto setMindmapPublished(UUID id, boolean published) {
        Mindmap mindmap = requireMindmap(id);
        mindmap.setPublished(published);
        return toDto(mindmapRepository.save(mindmap));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private School requireSchool(UUID id) {
        return schoolRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "School not found"));
    }

    private Module requireModule(UUID id) {
        return moduleRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));
    }

    private Course requireCourse(UUID id) {
        return courseRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    }

    private SourceExam requireSourceExam(UUID id) {
        return sourceExamRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source exam not found"));
    }

    private Mindmap requireMindmap(UUID id) {
        return mindmapRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mindmap not found"));
    }

    private <T extends com.vetspace.domain.common.BaseEntity> List<T> loadAllOrThrow(
        List<UUID> orderedIds, java.util.function.Function<List<UUID>, Iterable<T>> loader, String what) {
        Set<UUID> distinct = Set.copyOf(orderedIds);
        if (distinct.size() != orderedIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate ids in reorder list");
        }
        List<T> loaded = new java.util.ArrayList<>();
        loader.apply(orderedIds).forEach(loaded::add);
        if (loaded.size() != distinct.size()) {
            Set<UUID> found = loaded.stream().map(T::getId).collect(Collectors.toSet());
            distinct.stream().filter(id -> !found.contains(id)).findFirst().ifPresent(missing -> {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown " + what + " id: " + missing);
            });
        }
        return loaded;
    }

    private <T extends com.vetspace.domain.common.BaseEntity> void applyOrder(
        List<UUID> orderedIds, List<T> entities, java.util.function.ObjIntConsumer<T> positionSetter) {
        for (T entity : entities) {
            positionSetter.accept(entity, orderedIds.indexOf(entity.getId()) + 1);
        }
    }

    private SchoolDto toDto(School s) {
        return new SchoolDto(s.getId(), s.getName(), s.getSlug());
    }

    /**
     * Modules are unique on (study_year, name) now that content is national. Checking
     * here turns what the database would answer as a constraint-violation 500 into the
     * 409 an admin can act on. {@code selfId} lets an update keep its own name.
     */
    private void requireNameFreeForYear(Integer studyYear, String name, UUID selfId) {
        moduleRepository.findByStudyYearAndNameIgnoreCase(studyYear, name)
            .filter(existing -> !existing.getId().equals(selfId))
            .ifPresent(existing -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un module nommé « " + name + " » existe déjà pour cette année");
            });
    }

    private ModuleDto toDto(Module m) {
        return new ModuleDto(m.getId(), m.getStudyYear(), m.getName(), m.getPosition(), m.isPublished());
    }

    private CourseDto toDto(Course c) {
        return new CourseDto(c.getId(), c.getModule().getId(), c.getName(), c.getPosition(), c.isPublished(), c.isFreePreview());
    }

    private SourceExamDto toDto(SourceExam e) {
        return new SourceExamDto(e.getId(), e.getLabel(), e.getYear(), e.getExamType());
    }

    private MindmapDto toDto(Mindmap m) {
        return new MindmapDto(m.getId(), m.getCourse().getId(), m.getTitle(), m.getImageUrl(), m.isPublished(),
            m.getCreatedAt(), m.getUpdatedAt());
    }
}
