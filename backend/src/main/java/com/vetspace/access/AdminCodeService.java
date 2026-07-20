package com.vetspace.access;

import com.vetspace.access.dto.AccessDtos.CodeBatchDto;
import com.vetspace.access.dto.AccessDtos.CodeDto;
import com.vetspace.access.dto.AccessDtos.CodeStatus;
import com.vetspace.access.dto.AccessDtos.GenerateCodesRequest;
import com.vetspace.access.dto.AccessDtos.GenerateCodesResponse;
import com.vetspace.access.dto.AccessDtos.RevokeBatchResponse;
import com.vetspace.access.dto.AccessDtos.SubscriptionAuditDto;
import com.vetspace.domain.access.ActivationCode;
import com.vetspace.domain.access.CodeBatch;
import com.vetspace.domain.access.Pack;
import com.vetspace.domain.user.User;
import com.vetspace.repository.ActivationCodeRepository;
import com.vetspace.repository.CodeBatchRepository;
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
    private final CodeBatchRepository codeBatchRepository;

    public AdminCodeService(ActivationCodeRepository activationCodeRepository, PackRepository packRepository,
                             SubscriptionRepository subscriptionRepository, UserRepository userRepository,
                             CodeBatchStore codeBatchStore, CodeBatchRepository codeBatchRepository) {
        this.activationCodeRepository = activationCodeRepository;
        this.packRepository = packRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.codeBatchStore = codeBatchStore;
        this.codeBatchRepository = codeBatchRepository;
    }

    @Transactional
    public GenerateCodesResponse generate(UUID adminId, GenerateCodesRequest request) {
        Pack pack = packRepository.findById(request.packId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pack not found"));
        User creator = userRepository.findById(adminId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        int maxUses = request.maxUses() == null ? 1 : request.maxUses();

        // Recorded before the codes so every code can point at it: if the plaintext is
        // never downloaded, this row is the only way to find and revoke the batch.
        CodeBatch batch = codeBatchRepository.save(CodeBatch.builder()
            .pack(pack)
            .createdBy(creator)
            .codeCount(request.count())
            .maxUses(maxUses)
            .generatedAt(Instant.now())
            .build());

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
                .batch(batch)
                .build());
        }
        activationCodeRepository.saveAll(rows);
        String csvToken = codeBatchStore.store(plaintext, pack.getName(), batch.getId());
        return new GenerateCodesResponse(pack.getId(), plaintext.size(), plaintext, csvToken);
    }

    /**
     * Every batch, newest first, with what became of its codes. {@code downloaded} false
     * is the flag that matters: those plaintext codes were never saved anywhere.
     */
    @Transactional(readOnly = true)
    public List<CodeBatchDto> listBatches() {
        return codeBatchRepository.findAllByOrderByGeneratedAtDesc().stream()
            .map(batch -> {
                List<ActivationCode> codes = activationCodeRepository.findByBatchId(batch.getId());
                long revoked = codes.stream().filter(ActivationCode::isRevoked).count();
                long used = codes.stream()
                    .filter(c -> !c.isRevoked() && c.getUsedCount() >= c.getMaxUses()).count();
                long active = codes.size() - revoked - used;
                return new CodeBatchDto(batch.getId(), batch.getPack().getId(), batch.getPack().getName(),
                    batch.getCodeCount(), batch.getMaxUses(), batch.getGeneratedAt(),
                    batch.getDownloadedAt() != null, batch.getDownloadedAt(),
                    active, used, revoked);
            })
            .toList();
    }

    /**
     * Revokes every still-unused code in a batch — the way out of "generated codes, lost the
     * CSV". Codes that have already been redeemed are left alone: revoking them would strip
     * access from students who paid.
     */
    @Transactional
    public RevokeBatchResponse revokeBatch(UUID batchId) {
        codeBatchRepository.findById(batchId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found"));

        List<ActivationCode> toRevoke = activationCodeRepository.findByBatchId(batchId).stream()
            .filter(code -> !code.isRevoked())
            .filter(code -> code.getUsedCount() == 0)
            .toList();
        toRevoke.forEach(code -> code.setRevoked(true));
        activationCodeRepository.saveAll(toRevoke);
        return new RevokeBatchResponse(batchId, toRevoke.size());
    }

    /** One-shot CSV of a just-generated batch; second call for the same token finds nothing. */
    @Transactional
    public String csv(String token) {
        // Read the batch id BEFORE consuming: consume() removes the entry.
        UUID batchId = codeBatchStore.batchIdFor(token);
        List<String> codes = codeBatchStore.consume(token);
        if (codes == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found (already downloaded or expired)");
        }
        if (batchId != null) {
            // Records that the plaintext actually reached someone — the difference between
            // "sellable" and "must be revoked and regenerated".
            codeBatchRepository.findById(batchId).ifPresent(batch -> {
                batch.setDownloadedAt(Instant.now());
                codeBatchRepository.save(batch);
            });
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
