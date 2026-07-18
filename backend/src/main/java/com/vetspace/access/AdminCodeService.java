package com.vetspace.access;

import com.vetspace.access.dto.AccessDtos.CodeDto;
import com.vetspace.access.dto.AccessDtos.CodeStatus;
import com.vetspace.access.dto.AccessDtos.GenerateCodesRequest;
import com.vetspace.access.dto.AccessDtos.GenerateCodesResponse;
import com.vetspace.access.dto.AccessDtos.SubscriptionAuditDto;
import com.vetspace.domain.access.ActivationCode;
import com.vetspace.domain.access.Pack;
import com.vetspace.domain.user.User;
import com.vetspace.repository.ActivationCodeRepository;
import com.vetspace.repository.PackRepository;
import com.vetspace.repository.SubscriptionRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.TokenHasher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Code lifecycle for admins. Plaintext exists only in the generation response (and its one-shot CSV) — the DB holds SHA-256, and nothing here logs code values. */
@Service
public class AdminCodeService {

    private final ActivationCodeRepository activationCodeRepository;
    private final PackRepository packRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final CodeBatchStore codeBatchStore;

    public AdminCodeService(ActivationCodeRepository activationCodeRepository, PackRepository packRepository,
                             SubscriptionRepository subscriptionRepository, UserRepository userRepository,
                             CodeBatchStore codeBatchStore) {
        this.activationCodeRepository = activationCodeRepository;
        this.packRepository = packRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.codeBatchStore = codeBatchStore;
    }

    @Transactional
    public GenerateCodesResponse generate(UUID adminId, GenerateCodesRequest request) {
        Pack pack = packRepository.findById(request.packId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pack not found"));
        User creator = userRepository.findById(adminId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        int maxUses = request.maxUses() == null ? 1 : request.maxUses();

        List<String> plaintext = new ArrayList<>(request.count());
        List<ActivationCode> rows = new ArrayList<>(request.count());
        for (int i = 0; i < request.count(); i++) {
            String code = CodeGenerator.generate();
            plaintext.add(code);
            rows.add(ActivationCode.builder()
                .pack(pack)
                .codeHash(TokenHasher.sha256Hex(CodeGenerator.normalize(code)))
                .maxUses(maxUses)
                .usedCount(0)
                .revoked(false)
                .createdBy(creator)
                .build());
        }
        activationCodeRepository.saveAll(rows);
        String csvToken = codeBatchStore.store(plaintext, pack.getName());
        return new GenerateCodesResponse(pack.getId(), plaintext.size(), plaintext, csvToken);
    }

    /** One-shot CSV of a just-generated batch; second call for the same token finds nothing. */
    public String csv(String token) {
        List<String> codes = codeBatchStore.consume(token);
        if (codes == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found (already downloaded or expired)");
        }
        StringBuilder sb = new StringBuilder("code\n");
        codes.forEach(c -> sb.append(c).append('\n'));
        return sb.toString();
    }

    public Page<CodeDto> list(UUID packId, Pageable pageable) {
        Page<ActivationCode> page = packId == null
            ? activationCodeRepository.findAll(pageable)
            : activationCodeRepository.findByPackId(packId, pageable);
        return page.map(this::toDto);
    }

    @Transactional
    public CodeDto revoke(UUID codeId) {
        ActivationCode code = activationCodeRepository.findById(codeId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Code not found"));
        code.setRevoked(true);
        return toDto(activationCodeRepository.save(code));
    }

    public List<SubscriptionAuditDto> auditSubscriptions(String email) {
        return subscriptionRepository.findByUserEmail(email).stream()
            .map(s -> new SubscriptionAuditDto(s.getId(), s.getUser().getEmail(), s.getUser().getUsername(),
                s.getPack().getId(), s.getPack().getName(), s.getStartsAt(), s.getEndsAt(),
                s.getActivationCode().getId()))
            .toList();
    }

    private CodeDto toDto(ActivationCode c) {
        return new CodeDto(c.getId(), c.getPack().getId(), c.getPack().getName(), c.getMaxUses(), c.getUsedCount(),
            c.isRevoked(), statusOf(c), c.getCreatedAt());
    }

    private CodeStatus statusOf(ActivationCode c) {
        if (c.isRevoked()) {
            return CodeStatus.REVOKED;
        }
        if (c.getUsedCount() >= c.getMaxUses()) {
            return CodeStatus.EXHAUSTED;
        }
        if (!c.getPack().isActive() || c.getPack().getExpiresAt().isBefore(Instant.now())) {
            return CodeStatus.EXPIRED;
        }
        return CodeStatus.ACTIVE;
    }
}
