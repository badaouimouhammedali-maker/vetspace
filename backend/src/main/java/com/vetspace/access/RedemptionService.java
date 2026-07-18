package com.vetspace.access;

import com.vetspace.access.dto.AccessDtos.PublicPackDto;
import com.vetspace.access.dto.AccessDtos.RedeemResponse;
import com.vetspace.domain.access.ActivationCode;
import com.vetspace.domain.access.Pack;
import com.vetspace.domain.access.Subscription;
import com.vetspace.domain.user.User;
import com.vetspace.repository.ActivationCodeRepository;
import com.vetspace.repository.PackRepository;
import com.vetspace.repository.SubscriptionRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.TokenHasher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RedemptionService {

    /** One message for every failure mode: unknown, revoked, exhausted, inactive/expired pack — no oracle for attackers. */
    private static final String GENERIC_FAILURE = "Invalid activation code";

    private final ActivationCodeRepository activationCodeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PackRepository packRepository;
    private final UserRepository userRepository;

    public RedemptionService(ActivationCodeRepository activationCodeRepository,
                              SubscriptionRepository subscriptionRepository,
                              PackRepository packRepository, UserRepository userRepository) {
        this.activationCodeRepository = activationCodeRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.packRepository = packRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public RedeemResponse redeem(UUID userId, String rawCode) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));

        ActivationCode code = activationCodeRepository
            .findByCodeHash(TokenHasher.sha256Hex(CodeGenerator.normalize(rawCode)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, GENERIC_FAILURE));

        Pack pack = code.getPack();
        if (!pack.isActive() || pack.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, GENERIC_FAILURE);
        }
        // Atomic guard: the UPDATE re-checks revoked/used_count, so under a concurrent race
        // on the last remaining use, exactly one caller gets a row updated.
        if (activationCodeRepository.tryConsume(code.getId()) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, GENERIC_FAILURE);
        }

        Instant now = Instant.now();
        Subscription subscription = subscriptionRepository.save(Subscription.builder()
            .user(user)
            .pack(pack)
            .activationCode(code)
            .startsAt(now)
            // Fixed end date shared by the whole pack — "jusqu'à la fin de l'année universitaire".
            .endsAt(pack.getExpiresAt())
            .build());
        return new RedeemResponse(subscription.getId(), pack.getName(), subscription.getStartsAt(), subscription.getEndsAt());
    }

    /** Public pricing data: active, unexpired packs only. */
    public List<PublicPackDto> publicPacks(UUID schoolId, Integer studyYear) {
        Specification<Pack> spec = Specification.allOf(
            (root, query, cb) -> cb.isTrue(root.get("active")),
            (root, query, cb) -> cb.greaterThan(root.get("expiresAt"), Instant.now()),
            schoolId == null ? null : (root, query, cb) -> cb.equal(root.get("school").get("id"), schoolId),
            studyYear == null ? null : (root, query, cb) -> cb.equal(root.get("studyYear"), studyYear));
        return packRepository.findAll(spec, Sort.by("name")).stream()
            .map(p -> new PublicPackDto(p.getId(), p.getSchool().getId(), p.getStudyYear(), p.getName(),
                p.getAcademicYear(), p.getPriceDa(), p.getExpiresAt()))
            .toList();
    }
}
