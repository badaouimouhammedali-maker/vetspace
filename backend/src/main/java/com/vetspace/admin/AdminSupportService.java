package com.vetspace.admin;

import com.vetspace.admin.dto.AdminDtos.SupportMessageDto;
import com.vetspace.domain.extras.SupportMessage;
import com.vetspace.domain.user.User;
import com.vetspace.repository.SupportMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only support inbox for staff; replies happen out-of-band by email (Reply-To was set on submission). */
@Service
@Transactional(readOnly = true)
public class AdminSupportService {

    private final SupportMessageRepository supportMessageRepository;

    public AdminSupportService(SupportMessageRepository supportMessageRepository) {
        this.supportMessageRepository = supportMessageRepository;
    }

    public Page<SupportMessageDto> list(Pageable pageable) {
        return supportMessageRepository.findAll(pageable).map(this::toDto);
    }

    private SupportMessageDto toDto(SupportMessage m) {
        User u = m.getUser();
        return new SupportMessageDto(m.getId(), u.getEmail(), u.getUsername(),
            u.getFirstName() + " " + u.getLastName(), m.getSubject(), m.getBody(), m.getCreatedAt());
    }
}
