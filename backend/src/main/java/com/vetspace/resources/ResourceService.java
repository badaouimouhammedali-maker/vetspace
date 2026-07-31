package com.vetspace.resources;

import com.vetspace.domain.content.Module;
import com.vetspace.domain.content.Resource;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.repository.ModuleRepository;
import com.vetspace.repository.ResourceRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.resources.dto.ResourceDtos.ModuleResourceSummaryDto;
import com.vetspace.resources.dto.ResourceDtos.ModuleResourcesDto;
import com.vetspace.resources.dto.ResourceDtos.ResourceCreateRequest;
import com.vetspace.resources.dto.ResourceDtos.ResourceDto;
import com.vetspace.resources.dto.ResourceDtos.ResourceUpdateRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * The free study-resource library.
 *
 * <p>Note what this service never calls: {@code SubscriptionGate}. Study material is
 * free to any logged-in student — the paywall covers the QCM bank only. Authentication
 * is enforced at the controller; there is deliberately no second gate here.
 */
@Service
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final ModuleRepository moduleRepository;
    private final UserRepository userRepository;
    private final ResourceFileService fileService;

    public ResourceService(ResourceRepository resourceRepository, ModuleRepository moduleRepository,
                           UserRepository userRepository, ResourceFileService fileService) {
        this.resourceRepository = resourceRepository;
        this.moduleRepository = moduleRepository;
        this.userRepository = userRepository;
        this.fileService = fileService;
    }

    // ---------------------------------------------------------------
    // Student reads — authenticated, never subscription-gated
    // ---------------------------------------------------------------

    /** Study years that actually have something published, for the year selector. */
    public List<Integer> yearsWithResources() {
        return resourceRepository.findYearsWithPublishedResources();
    }

    /**
     * Published resources for a year, grouped by module. An empty module is omitted
     * rather than returned empty: the UI's per-module empty state is for a module the
     * student opened, not for noise in a list.
     */
    public List<ModuleResourcesDto> listForStudents(Integer studyYear, UUID moduleId) {
        List<Resource> resources = moduleId != null
            ? resourceRepository.findByModuleIdAndPublishedTrueOrderByPositionAsc(moduleId)
            : resourceRepository.findPublishedByStudyYear(studyYear);

        Map<UUID, List<Resource>> byModule = new LinkedHashMap<>();
        for (Resource r : resources) {
            byModule.computeIfAbsent(r.getModule().getId(), k -> new ArrayList<>()).add(r);
        }
        return byModule.values().stream()
            .map(group -> {
                Module module = group.get(0).getModule();
                return new ModuleResourcesDto(module.getId(), module.getName(), module.getStudyYear(),
                    module.getPosition(), group.stream().map(this::toDto).toList());
            })
            .sorted(Comparator.comparing(ModuleResourcesDto::modulePosition,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    /** A single resource. Unpublished is a 404 for students — never a 403. */
    public ResourceDto getForCaller(UUID callerId, UUID id) {
        Resource resource = require(id);
        User caller = userRepository.findById(callerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        boolean staff = caller.getRole() == Role.ADMIN || caller.getRole() == Role.TEACHER;
        if (!resource.isPublished() && !staff) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
        }
        return toDto(resource);
    }

    // ---------------------------------------------------------------
    // Admin
    // ---------------------------------------------------------------

    @Transactional
    public ResourceDto create(UUID uploaderId, ResourceCreateRequest request, MultipartFile file) {
        Module module = requireModule(request.moduleId());
        // Store first: a row pointing at a file that failed to upload would be a broken
        // download link, whereas a stored object with no row is invisible and cheap.
        ResourceFileService.StoredFile stored = fileService.store(file);
        Resource resource = Resource.builder()
            .module(module)
            .title(request.title())
            .description(request.description())
            .fileUrl(stored.url())
            .fileType(stored.fileType())
            .fileSizeBytes(stored.sizeBytes())
            .position(resourceRepository.maxPosition(module.getId()) + 1)
            .published(request.published())
            .uploadedBy(userRepository.findById(uploaderId).orElse(null))
            .build();
        // saveAndFlush, not save: inside a transaction `save` on a new entity only calls
        // persist(), and @CreationTimestamp/@UpdateTimestamp are generated at flush. The
        // DTO built from the un-flushed entity carries createdAt/updatedAt = null, which
        // the client's schema rejects — so an upload that WAS stored reports an error and
        // invites the admin to upload the same file again. Same trap as question create.
        return toDto(resourceRepository.saveAndFlush(resource));
    }

    public List<ResourceDto> listForAdmin(UUID moduleId) {
        return resourceRepository.findByModuleIdOrderByPositionAsc(moduleId).stream()
            .map(this::toDto).toList();
    }

    /** Per-module counts and byte totals, so R2 usage is visible before it surprises anyone. */
    public List<ModuleResourceSummaryDto> summaries() {
        return moduleRepository.findAll().stream()
            .map(m -> new ModuleResourceSummaryDto(m.getId(), m.getName(), m.getStudyYear(),
                resourceRepository.findByModuleIdOrderByPositionAsc(m.getId()).size(),
                resourceRepository.totalBytesForModule(m.getId())))
            .sorted(Comparator.comparing(ModuleResourceSummaryDto::studyYear)
                .thenComparing(ModuleResourceSummaryDto::moduleName))
            .toList();
    }

    @Transactional
    public ResourceDto update(UUID id, ResourceUpdateRequest request) {
        Resource resource = require(id);
        resource.setModule(requireModule(request.moduleId()));
        resource.setTitle(request.title());
        resource.setDescription(request.description());
        resource.setPublished(request.published());
        return toDto(resourceRepository.save(resource));
    }

    @Transactional
    public ResourceDto setPublished(UUID id, boolean published) {
        Resource resource = require(id);
        resource.setPublished(published);
        return toDto(resourceRepository.save(resource));
    }

    /**
     * Deletes the row and the stored object.
     *
     * <p>Row first, then storage: if the delete of the object fails we have lost an
     * orphaned file in a bucket, which costs pennies and can be swept. The other order
     * risks a row whose file is gone — a download button that 404s for every student.
     */
    @Transactional
    public void delete(UUID id) {
        Resource resource = require(id);
        String url = resource.getFileUrl();
        resourceRepository.delete(resource);
        fileService.deleteByUrl(url);
    }

    @Transactional
    public List<ResourceDto> reorder(List<UUID> orderedIds) {
        List<Resource> resources = resourceRepository.findAllById(orderedIds);
        if (resources.size() != orderedIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown resource in the ordering");
        }
        Map<UUID, Resource> byId = new LinkedHashMap<>();
        resources.forEach(r -> byId.put(r.getId(), r));
        for (int i = 0; i < orderedIds.size(); i++) {
            byId.get(orderedIds.get(i)).setPosition(i + 1);
        }
        return resourceRepository.saveAll(byId.values()).stream().map(this::toDto).toList();
    }

    private Resource require(UUID id) {
        return resourceRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
    }

    private Module requireModule(UUID id) {
        return moduleRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));
    }

    private ResourceDto toDto(Resource r) {
        Module m = r.getModule();
        return new ResourceDto(r.getId(), m.getId(), m.getName(), m.getStudyYear(), r.getTitle(),
            r.getDescription(), r.getFileUrl(), r.getFileType(), r.getFileSizeBytes(),
            r.getPosition(), r.isPublished(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
