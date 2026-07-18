package com.vetspace.extras;

import com.vetspace.domain.extras.SupportMessage;
import com.vetspace.domain.user.User;
import com.vetspace.extras.dto.ExtrasDtos.SupportRequest;
import com.vetspace.mail.AppMailSender;
import com.vetspace.repository.SupportMessageRepository;
import com.vetspace.repository.UserRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Support inbox: message is stored AND forwarded to the configured inbox with Reply-To = the student. */
@Service
public class SupportService {

    public static final int DAILY_LIMIT = 5;
    private static final String RATE_SCOPE = "support";

    private final SupportMessageRepository supportMessageRepository;
    private final UserRepository userRepository;
    private final AppMailSender mailSender;
    private final DailyUserRateLimiter rateLimiter;
    private final String supportInbox;

    public SupportService(SupportMessageRepository supportMessageRepository, UserRepository userRepository,
                           AppMailSender mailSender, DailyUserRateLimiter rateLimiter,
                           @Value("${app.support.inbox}") String supportInbox) {
        this.supportMessageRepository = supportMessageRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.rateLimiter = rateLimiter;
        this.supportInbox = supportInbox;
    }

    @Transactional
    public void submit(UUID userId, SupportRequest request) {
        rateLimiter.check(RATE_SCOPE, userId, DAILY_LIMIT);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        supportMessageRepository.save(SupportMessage.builder()
            .user(user)
            .subject(request.subject())
            .body(request.body())
            .build());
        mailSender.send(supportInbox,
            "[Support] " + request.subject(),
            "De: " + user.getFirstName() + " " + user.getLastName() + " (" + user.getEmail() + ")\n\n" + request.body(),
            user.getEmail());
    }
}
