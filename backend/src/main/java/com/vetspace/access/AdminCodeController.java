package com.vetspace.access;

import com.vetspace.access.dto.AccessDtos.CodeBatchDto;
import com.vetspace.access.dto.AccessDtos.CodeDto;
import com.vetspace.access.dto.AccessDtos.GenerateCodesRequest;
import com.vetspace.access.dto.AccessDtos.GenerateCodesResponse;
import com.vetspace.access.dto.AccessDtos.RevokeBatchResponse;
import com.vetspace.access.dto.AccessDtos.SubscriptionAuditDto;
import com.vetspace.web.PageResponse;
import com.vetspace.web.Paging;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Commercial objects: ADMIN only, like packs. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCodeController {

    private final AdminCodeService service;

    public AdminCodeController(AdminCodeService service) {
        this.service = service;
    }

    @PostMapping("/codes")
    @ResponseStatus(HttpStatus.CREATED)
    public GenerateCodesResponse generate(@AuthenticationPrincipal UUID adminId,
                                           @Valid @RequestBody GenerateCodesRequest request) {
        return service.generate(adminId, request);
    }

    /** One-shot: the token from the generation response works exactly once, then 404. */
    @GetMapping("/codes/csv/{token}")
    public ResponseEntity<String> csv(@PathVariable String token) {
        String csv = service.csv(token);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"activation-codes.csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv);
    }

    @GetMapping("/codes")
    public PageResponse<CodeDto> list(@RequestParam(required = false) UUID packId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(packId, Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @PostMapping("/codes/{id}/revoke")
    public CodeDto revoke(@PathVariable UUID id) {
        return service.revoke(id);
    }

    @GetMapping("/codes/batches")
    public List<CodeBatchDto> listBatches() {
        return service.listBatches();
    }

    /** Revokes every unused code of a batch — recovery for a batch whose CSV was lost. */
    @PostMapping("/codes/batches/{id}/revoke")
    public RevokeBatchResponse revokeBatch(@PathVariable UUID id) {
        return service.revokeBatch(id);
    }

    @GetMapping("/subscriptions")
    public List<SubscriptionAuditDto> auditSubscriptions(@RequestParam String email) {
        return service.auditSubscriptions(email);
    }
}
