package com.vetspace.admin;

import com.vetspace.admin.dto.PackDtos.PackDto;
import com.vetspace.admin.dto.PackDtos.PackRequest;
import com.vetspace.domain.access.Pack;
import com.vetspace.repository.PackRepository;
import com.vetspace.repository.SchoolRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PackAdminService {

    private final PackRepository packRepository;
    private final SchoolRepository schoolRepository;

    public PackAdminService(PackRepository packRepository, SchoolRepository schoolRepository) {
        this.packRepository = packRepository;
        this.schoolRepository = schoolRepository;
    }

    @Transactional
    public PackDto create(PackRequest request) {
        Pack pack = Pack.builder()
            .school(schoolRepository.findById(request.schoolId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "School not found")))
            .studyYear(request.studyYear())
            .name(request.name())
            .academicYear(request.academicYear())
            .priceDa(request.priceDa())
            .active(request.active())
            .expiresAt(request.expiresAt())
            .build();
        return toDto(packRepository.save(pack));
    }

    public Page<PackDto> list(Pageable pageable) {
        return packRepository.findAll(pageable).map(this::toDto);
    }

    public PackDto get(UUID id) {
        return toDto(require(id));
    }

    @Transactional
    public PackDto update(UUID id, PackRequest request) {
        Pack pack = require(id);
        pack.setSchool(schoolRepository.findById(request.schoolId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "School not found")));
        pack.setStudyYear(request.studyYear());
        pack.setName(request.name());
        pack.setAcademicYear(request.academicYear());
        pack.setPriceDa(request.priceDa());
        pack.setActive(request.active());
        pack.setExpiresAt(request.expiresAt());
        return toDto(packRepository.save(pack));
    }

    @Transactional
    public void delete(UUID id) {
        packRepository.delete(require(id));
    }

    private Pack require(UUID id) {
        return packRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pack not found"));
    }

    private PackDto toDto(Pack p) {
        return new PackDto(p.getId(), p.getSchool().getId(), p.getStudyYear(), p.getName(), p.getAcademicYear(),
            p.getPriceDa(), p.isActive(), p.getExpiresAt());
    }
}
