package com.vetspace.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.vetspace.access.dto.AccessDtos.CodeBatchDto;
import com.vetspace.access.dto.AccessDtos.GenerateCodesRequest;
import com.vetspace.access.dto.AccessDtos.GenerateCodesResponse;
import com.vetspace.access.dto.AccessDtos.RevokeBatchResponse;
import com.vetspace.domain.access.ActivationCode;
import com.vetspace.domain.access.Pack;
import com.vetspace.domain.school.School;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.repository.ActivationCodeRepository;
import com.vetspace.repository.PackRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Batch tracking exists for one scenario: an admin generates codes, never downloads the
 * CSV, and the plaintext is gone. Those codes are unsellable but still occupy the pack, so
 * there has to be a way to identify and retire them.
 */
@SpringBootTest
@Testcontainers
class CodeBatchIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("app.recaptcha.enabled", () -> false);
        registry.add("app.auth.auto-verify-emails", () -> true);
    }

    @Autowired
    private AdminCodeService service;

    @Autowired
    private ActivationCodeRepository activationCodeRepository;

    @Autowired
    private PackRepository packRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID adminId;
    private UUID packId;

    @BeforeEach
    void setUp() {
        School school = schoolRepository.save(School.builder()
            .name("ENSV Alger").slug("ensv-" + UUID.randomUUID()).build());
        User admin = userRepository.save(User.builder()
            .email("batch-" + UUID.randomUUID() + "@vetspace.dz")
            .username("batch-" + UUID.randomUUID().toString().substring(0, 8))
            .passwordHash(passwordEncoder.encode("a-long-enough-password"))
            .lastName("Admin").firstName("Batch")
            .role(Role.ADMIN).status(UserStatus.ACTIVE).emailVerified(true)
            .build());
        adminId = admin.getId();
        Pack pack = packRepository.save(Pack.builder()
            .school(school).studyYear(3).name("Pack test " + UUID.randomUUID())
            .academicYear("2026-2027").priceDa(3500).active(true)
            .expiresAt(Instant.now().plus(300, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS))
            .build());
        packId = pack.getId();
    }

    private GenerateCodesResponse generate(int count) {
        return service.generate(adminId, new GenerateCodesRequest(packId, count, 1));
    }

    private CodeBatchDto batchFor(GenerateCodesResponse response) {
        // The generation response does not carry the batch id, so find it by pack + count.
        return service.listBatches().stream()
            .filter(b -> b.packId().equals(packId) && b.codeCount() == response.count())
            .findFirst().orElseThrow();
    }

    @Test
    void aGeneratedBatchIsRecordedAsNotYetDownloaded() {
        GenerateCodesResponse response = generate(4);

        CodeBatchDto batch = batchFor(response);
        assertThat(batch.codeCount()).isEqualTo(4);
        assertThat(batch.generatedAt()).isNotNull();
        // The signal that the plaintext was never saved anywhere.
        assertThat(batch.downloaded()).isFalse();
        assertThat(batch.downloadedAt()).isNull();
        assertThat(batch.activeCount()).isEqualTo(4);
    }

    @Test
    void fetchingTheCsvMarksTheBatchDownloaded() {
        GenerateCodesResponse response = generate(3);

        service.csv(response.csvToken());

        CodeBatchDto batch = batchFor(response);
        assertThat(batch.downloaded()).isTrue();
        assertThat(batch.downloadedAt()).isNotNull();
    }

    @Test
    void revokingABatchRetiresEveryUnusedCode() {
        GenerateCodesResponse response = generate(5);
        UUID batchId = batchFor(response).id();

        RevokeBatchResponse revoked = service.revokeBatch(batchId);

        assertThat(revoked.revokedCount()).isEqualTo(5);
        assertThat(activationCodeRepository.findByBatchId(batchId))
            .allMatch(ActivationCode::isRevoked);
        CodeBatchDto after = batchFor(response);
        assertThat(after.revokedCount()).isEqualTo(5);
        assertThat(after.activeCount()).isZero();
    }

    @Test
    void revokingABatchLeavesAlreadyRedeemedCodesAlone() {
        GenerateCodesResponse response = generate(3);
        UUID batchId = batchFor(response).id();

        // One code has been sold and redeemed; revoking it would strip access from a
        // student who paid, so it must survive the batch revoke.
        List<ActivationCode> codes = activationCodeRepository.findByBatchId(batchId);
        ActivationCode redeemed = codes.get(0);
        redeemed.setUsedCount(1);
        activationCodeRepository.save(redeemed);

        RevokeBatchResponse revoked = service.revokeBatch(batchId);

        assertThat(revoked.revokedCount()).isEqualTo(2);
        assertThat(activationCodeRepository.findById(redeemed.getId()).orElseThrow().isRevoked())
            .isFalse();
    }

    @Test
    void revokingIsIdempotent() {
        GenerateCodesResponse response = generate(2);
        UUID batchId = batchFor(response).id();

        assertThat(service.revokeBatch(batchId).revokedCount()).isEqualTo(2);
        // Second call finds nothing left to revoke rather than double-counting or failing.
        assertThat(service.revokeBatch(batchId).revokedCount()).isZero();
    }
}
