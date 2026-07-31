package com.vetspace.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetspace.domain.content.FileType;
import com.vetspace.domain.content.Module;
import com.vetspace.domain.content.Resource;
import com.vetspace.domain.school.School;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.media.MediaStorage;
import com.vetspace.repository.ModuleRepository;
import com.vetspace.repository.ResourceRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The free study library: what students may read without paying, and what an upload is
 * allowed to be.
 *
 * <p>The subscription-free student here has no subscription at all — that is the point
 * of the feature, and the test that would fail if somebody later "tidied up" by adding
 * the gate to these endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ResourceLibraryIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("app.recaptcha.enabled", () -> false);
        registry.add("app.auth.auto-verify-emails", () -> true);
        // Small enough to exercise the oversize path without allocating 25MB in a test.
        registry.add("app.resources.max-file-size-mb", () -> 1);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private ModuleRepository moduleRepository;
    @Autowired private ResourceRepository resourceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private RecordingStorage storage;

    private final String moduleName = "Anatomie " + UUID.randomUUID();
    private Module module;
    private String adminToken;
    /** Deliberately owns no subscription anywhere in this class. */
    private String brokeStudentToken;

    @BeforeEach
    void setUp() {
        storage.uploads.clear();
        storage.deleted.clear();
        School school = schoolRepository.save(School.builder()
            .name("ENSV").slug("ensv-" + UUID.randomUUID()).build());
        module = moduleRepository.save(Module.builder()
            .studyYear(3).name(moduleName).position(1).published(true).build());
        adminToken = jwtService.generateAccessToken(user(Role.ADMIN, school));
        brokeStudentToken = jwtService.generateAccessToken(user(Role.STUDENT, school));
    }

    private User user(Role role, School school) {
        return userRepository.save(User.builder()
            .email("res-" + UUID.randomUUID() + "@vetspace.dz")
            .username("res" + UUID.randomUUID().toString().substring(0, 8))
            .passwordHash(passwordEncoder.encode("correct-horse-battery"))
            .lastName("T").firstName("R")
            .role(role).status(UserStatus.ACTIVE).emailVerified(true)
            .school(school).studyYear(3)
            .build());
    }

    private MockMultipartFile metadata(String title, boolean published) throws Exception {
        return new MockMultipartFile("metadata", "metadata", MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(Map.of(
                "moduleId", module.getId().toString(), "title", title, "published", published)));
    }

    private static byte[] pdfBytes() {
        byte[] header = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[512];
        System.arraycopy(header, 0, out, 0, header.length);
        return out;
    }

    // ---------------------------------------------------------------
    // Upload validation
    // ---------------------------------------------------------------

    @Test
    void acceptsARealPdfAndRecordsItsTypeAndSize() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/admin/resources")
                .file(new MockMultipartFile("file", "cours.pdf", "application/pdf", pdfBytes()))
                .file(metadata("Cours d'anatomie", true))
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fileType").value("PDF"))
            .andExpect(jsonPath("$.fileSizeBytes").value(512))
            // Audit timestamps must be present on the CREATE response, not just on
            // subsequent reads: the client's schema requires them, so a null here turns
            // a successful upload into an error toast and a duplicate re-upload.
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andReturn();

        String url = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .get("fileUrl").asText();
        // Random key under resources/, never the uploaded filename.
        assertThat(url).contains("/resources/").endsWith(".pdf").doesNotContain("cours.pdf");
        assertThat(storage.uploads).hasSize(1);
        assertThat(storage.uploads.get(0).contentType()).isEqualTo("application/pdf");
    }

    /**
     * The one that matters: a file *named* .pdf, *declared* application/pdf, whose bytes
     * are a Windows executable. Only magic-byte sniffing catches it.
     */
    @Test
    void rejectsAnExecutableDisguisedAsAPdf() throws Exception {
        byte[] exe = new byte[256];
        exe[0] = 'M';
        exe[1] = 'Z';   // DOS/PE header

        mockMvc.perform(multipart("/api/admin/resources")
                .file(new MockMultipartFile("file", "cours.pdf", "application/pdf", exe))
                .file(metadata("Piège", true))
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());

        assertThat(storage.uploads).as("nothing may reach storage").isEmpty();
        // Scoped to this module: findAll() would also see rows the sibling tests wrote,
        // since the class shares one database.
        assertThat(resourceRepository.findByModuleIdOrderByPositionAsc(module.getId())).isEmpty();
    }

    @Test
    void rejectsAFileOverTheSizeLimit() throws Exception {
        byte[] big = new byte[2 * 1024 * 1024 + 1];   // limit is 1MB in this suite
        System.arraycopy("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), 0, big, 0, 9);

        mockMvc.perform(multipart("/api/admin/resources")
                .file(new MockMultipartFile("file", "gros.pdf", "application/pdf", big))
                .file(metadata("Trop gros", true))
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isPayloadTooLarge());

        assertThat(storage.uploads).isEmpty();
    }

    @Test
    void studentsCannotUpload() throws Exception {
        mockMvc.perform(multipart("/api/admin/resources")
                .file(new MockMultipartFile("file", "c.pdf", "application/pdf", pdfBytes()))
                .file(metadata("Interdit", true))
                .header("Authorization", "Bearer " + brokeStudentToken))
            .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // Student reads — free
    // ---------------------------------------------------------------

    @Test
    void aStudentWithNoSubscriptionCanReadTheLibrary() throws Exception {
        Resource published = save("Polycopié", true);

        mockMvc.perform(get("/api/resources?studyYear=3")
                .header("Authorization", "Bearer " + brokeStudentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].moduleName").value(moduleName))
            .andExpect(jsonPath("$[0].resources[0].title").value("Polycopié"));

        // And the single-resource read, which carries the file URL.
        mockMvc.perform(get("/api/resources/" + published.getId())
                .header("Authorization", "Bearer " + brokeStudentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fileUrl").value(published.getFileUrl()));

        // Sanity: the same student is genuinely locked out of the paid half.
        mockMvc.perform(get("/api/questions/count")
                .header("Authorization", "Bearer " + brokeStudentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void unpublishedResourcesAreInvisibleToStudents() throws Exception {
        Resource hidden = save("Brouillon", false);
        save("Visible", true);

        MvcResult result = mockMvc.perform(get("/api/resources?studyYear=3")
                .header("Authorization", "Bearer " + brokeStudentToken))
            .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.toString()).contains("Visible").doesNotContain("Brouillon");

        // Not 403 — a student must not be able to tell a hidden resource from a missing one.
        mockMvc.perform(get("/api/resources/" + hidden.getId())
                .header("Authorization", "Bearer " + brokeStudentToken))
            .andExpect(status().isNotFound());

        // Staff can still fetch it.
        mockMvc.perform(get("/api/resources/" + hidden.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void yearsListOnlyCoversYearsThatHaveSomethingPublished() throws Exception {
        // A year of its own: the endpoint is global, and year 3 is what every other test
        // in this class publishes into.
        Module year7 = moduleRepository.save(Module.builder()
            .studyYear(7).name("Année 7 " + UUID.randomUUID()).position(1).published(true).build());
        Resource draft = resourceRepository.save(Resource.builder()
            .module(year7).title("Brouillon")
            .fileUrl("http://localhost:9000/vetspace/resources/" + UUID.randomUUID() + ".pdf")
            .fileType(FileType.PDF).fileSizeBytes(10L).position(1).published(false).build());

        assertThat(years()).as("a year with only drafts must not appear").doesNotContain(7);

        draft.setPublished(true);
        resourceRepository.save(draft);

        assertThat(years()).contains(7);
    }

    private List<Integer> years() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/resources/years")
                .header("Authorization", "Bearer " + brokeStudentToken))
            .andExpect(status().isOk()).andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<Integer> out = new java.util.ArrayList<>();
        json.forEach(n -> out.add(n.asInt()));
        return out;
    }

    @Test
    void anonymousCallersGetNothing() throws Exception {
        save("Polycopié", true);
        mockMvc.perform(get("/api/resources?studyYear=3")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // Delete
    // ---------------------------------------------------------------

    @Test
    void deletingAResourceAlsoDeletesTheStoredObject() throws Exception {
        MvcResult created = mockMvc.perform(multipart("/api/admin/resources")
                .file(new MockMultipartFile("file", "cours.pdf", "application/pdf", pdfBytes()))
                .file(metadata("À supprimer", true))
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated()).andReturn();
        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8));
        UUID id = UUID.fromString(body.get("id").asText());
        String storedKey = storage.uploads.get(0).key();

        mockMvc.perform(delete("/api/admin/resources/" + id)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        // Row gone AND the bucket object gone — an orphaned file is a bill nobody reads.
        assertThat(resourceRepository.findById(id)).isEmpty();
        assertThat(storage.deleted).containsExactly(storedKey);
    }

    @Test
    void theStorageKeyIsRecoveredFromTheUrlEvenIfTheBaseUrlChanged() {
        // R2 public URLs change when a custom domain is attached; rows written before
        // the change must stay deletable.
        assertThat(ResourceFileService.keyFromUrl("https://new-cdn.vetspace.dz/resources/abc.pdf"))
            .isEqualTo("resources/abc.pdf");
        assertThat(ResourceFileService.keyFromUrl("https://x/media/img.png")).isNull();
    }

    private Resource save(String title, boolean published) {
        return resourceRepository.save(Resource.builder()
            .module(module).title(title)
            .fileUrl("http://localhost:9000/vetspace/resources/" + UUID.randomUUID() + ".pdf")
            .fileType(FileType.PDF).fileSizeBytes(1024L)
            .position(resourceRepository.maxPosition(module.getId()) + 1)
            .published(published)
            .build());
    }

    @TestConfiguration
    static class StorageConfig {
        @Bean
        @Primary
        RecordingStorage recordingStorage() {
            return new RecordingStorage();
        }
    }

    static class RecordingStorage implements MediaStorage {
        record Upload(String key, int size, String contentType) {
        }

        final List<Upload> uploads = new CopyOnWriteArrayList<>();
        final List<String> deleted = new CopyOnWriteArrayList<>();

        @Override
        public void put(String key, byte[] bytes, String contentType) {
            uploads.add(new Upload(key, bytes.length, contentType));
        }

        @Override
        public void delete(String key) {
            deleted.add(key);
        }
    }
}
