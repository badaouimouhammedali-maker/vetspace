package com.vetspace.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetspace.domain.content.Course;
import com.vetspace.domain.content.Module;
import com.vetspace.domain.school.School;
import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import com.vetspace.domain.user.UserStatus;
import com.vetspace.media.MediaStorage;
import com.vetspace.repository.CourseRepository;
import com.vetspace.repository.ModuleRepository;
import com.vetspace.repository.SchoolRepository;
import com.vetspace.repository.UserRepository;
import com.vetspace.security.JwtService;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AdminCatalogIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("app.recaptcha.enabled", () -> false);
        // Registration now sends a verification email and login requires a verified
        // address; these suites predate that and are not testing it.
        registry.add("app.auth.auto-verify-emails", () -> true);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private ModuleRepository moduleRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RecordingMediaStorage mediaStorage;

    private School school;
    private String adminToken;
    private String teacherToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        mediaStorage.uploads.clear();
        school = schoolRepository.save(School.builder()
            .name("ENSV Alger")
            .slug("ensv-" + UUID.randomUUID())
            .build());
        adminToken = tokenFor(Role.ADMIN);
        teacherToken = tokenFor(Role.TEACHER);
        studentToken = tokenFor(Role.STUDENT);
    }

    private String tokenFor(Role role) {
        User user = userRepository.save(User.builder()
            .email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com")
            .username(role.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8))
            .passwordHash("$2a$12$0000000000000000000000000000000000000000000000000000")
            .lastName("Test")
            .firstName(role.name())
            .role(role)
            .status(UserStatus.ACTIVE)
            .school(school)
            .studyYear(role == Role.STUDENT ? 3 : null)
            .emailVerified(true)
            .build());
        return jwtService.generateAccessToken(user);
    }

    // ---------------------------------------------------------------
    // Role enforcement
    // ---------------------------------------------------------------

    @Test
    void studentCrudIsForbidden() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "X", "slug", "x-" + UUID.randomUUID()));
        mockMvc.perform(post("/api/admin/schools").header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/modules").header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/schools/" + school.getId()).header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());

        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", pngBytes());
        mockMvc.perform(multipart("/api/admin/media").file(file).header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void teacherCanManageCatalogButNotPacks() throws Exception {
        String moduleBody = objectMapper.writeValueAsString(Map.of(
            "studyYear", 2, "name", "Physio", "published", false));
        mockMvc.perform(post("/api/admin/modules").header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON).content(moduleBody))
            .andExpect(status().isCreated());

        String packBody = objectMapper.writeValueAsString(Map.of(
            "studyYear", 2, "name", "Pack", "academicYear", "2026-2027",
            "priceDa", 3000, "active", true, "expiresAt", "2027-09-01T00:00:00Z"));
        mockMvc.perform(post("/api/admin/packs").header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON).content(packBody))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/packs").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(packBody))
            .andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------
    // Publish visibility
    // ---------------------------------------------------------------

    /**
     * National uniqueness: two modules cannot share (study_year, name) any more. Without
     * this check the database answers with a constraint violation, which the admin sees
     * as an unexplained 500.
     */
    @Test
    void duplicateModuleNameForTheSameYearIsRejected() throws Exception {
        String name = "Physiologie " + UUID.randomUUID();
        String body = objectMapper.writeValueAsString(Map.of(
            "studyYear", 6, "name", name, "published", false));

        mockMvc.perform(post("/api/admin/modules").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/modules").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());

        // The same name in another year stays legal — year is the scope now.
        String otherYear = objectMapper.writeValueAsString(Map.of(
            "studyYear", 7, "name", name, "published", false));
        mockMvc.perform(post("/api/admin/modules").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(otherYear))
            .andExpect(status().isCreated());
    }

    @Test
    void studentsSeeOnlyPublishedModulesAndCourses() throws Exception {
        // Unique names, and presence assertions rather than list sizes: the catalogue is
        // national now, so /api/modules?studyYear=4 also returns whatever every other
        // test class seeded for that year.
        String publishedName = "Anatomie " + UUID.randomUUID();
        String unpublishedName = "Brouillon " + UUID.randomUUID();
        Module published = moduleRepository.save(module(publishedName, 4, true));
        Module unpublished = moduleRepository.save(module(unpublishedName, 4, false));
        courseRepository.save(course(published, "Ostéologie", 1, true));
        courseRepository.save(course(published, "Brouillon cours", 2, false));

        MvcResult studentList = mockMvc.perform(get("/api/modules?studyYear=4")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode studentModules = objectMapper.readTree(studentList.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(names(studentModules)).contains(publishedName).doesNotContain(unpublishedName);

        MvcResult adminList = mockMvc.perform(get("/api/modules?studyYear=4")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(names(objectMapper.readTree(adminList.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))))
            .contains(publishedName, unpublishedName);

        MvcResult studentCourses = mockMvc.perform(get("/api/modules/" + published.getId() + "/courses")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode courses = objectMapper.readTree(studentCourses.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(courses).hasSize(1);
        assertThat(courses.get(0).get("name").asText()).isEqualTo("Ostéologie");

        // Unpublished module reads as nonexistent for students.
        mockMvc.perform(get("/api/modules/" + unpublished.getId() + "/courses")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/modules/" + unpublished.getId() + "/courses")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // Reorder
    // ---------------------------------------------------------------

    @Test
    void reorderPersistsNewPositions() throws Exception {
        Module m1 = moduleRepository.save(module("Premier", 5, true));
        Module m2 = moduleRepository.save(module("Deuxième", 5, true));
        Module m3 = moduleRepository.save(module("Troisième", 5, true));

        String body = objectMapper.writeValueAsString(Map.of(
            "orderedIds", List.of(m3.getId().toString(), m1.getId().toString(), m2.getId().toString())));
        mockMvc.perform(patch("/api/admin/modules/reorder").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        assertThat(moduleRepository.findById(m3.getId()).orElseThrow().getPosition()).isEqualTo(1);
        assertThat(moduleRepository.findById(m1.getId()).orElseThrow().getPosition()).isEqualTo(2);
        assertThat(moduleRepository.findById(m2.getId()).orElseThrow().getPosition()).isEqualTo(3);

        MvcResult list = mockMvc.perform(get("/api/modules?studyYear=5")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode modules = objectMapper.readTree(list.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(modules.get(0).get("name").asText()).isEqualTo("Troisième");
        assertThat(modules.get(1).get("name").asText()).isEqualTo("Premier");
        assertThat(modules.get(2).get("name").asText()).isEqualTo("Deuxième");
    }

    @Test
    void reorderWithUnknownIdIs404() throws Exception {
        Module m1 = moduleRepository.save(module("Seul", 6, true));
        String body = objectMapper.writeValueAsString(Map.of(
            "orderedIds", List.of(m1.getId().toString(), UUID.randomUUID().toString())));
        mockMvc.perform(patch("/api/admin/modules/reorder").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------
    // Media
    // ---------------------------------------------------------------

    @Test
    void mediaAcceptsRealPngAndReturnsUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes());
        mockMvc.perform(multipart("/api/admin/media").file(file).header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.url").exists());

        assertThat(mediaStorage.uploads).hasSize(1);
        assertThat(mediaStorage.uploads.get(0).key()).matches("media/[0-9a-f-]{36}\\.png");
        assertThat(mediaStorage.uploads.get(0).contentType()).isEqualTo("image/png");
    }

    @Test
    void mediaRejectsPdfBytesNamedPng() throws Exception {
        byte[] pdf = "%PDF-1.4 fake pdf content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "innocent.png", "image/png", pdf);
        mockMvc.perform(multipart("/api/admin/media").file(file).header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
        assertThat(mediaStorage.uploads).isEmpty();
    }

    @Test
    void mediaRejectsOversizeUpload() throws Exception {
        byte[] big = new byte[(int) (5 * 1024 * 1024) + 1];
        big[0] = (byte) 0x89; big[1] = 'P'; big[2] = 'N'; big[3] = 'G';
        big[4] = 0x0D; big[5] = 0x0A; big[6] = 0x1A; big[7] = 0x0A;
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", big);
        mockMvc.perform(multipart("/api/admin/media").file(file).header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isPayloadTooLarge());
        assertThat(mediaStorage.uploads).isEmpty();
    }

    // ---------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------

    /** Module names in a JSON array, so assertions can ignore rows other tests seeded. */
    private static List<String> names(JsonNode modules) {
        List<String> out = new java.util.ArrayList<>();
        modules.forEach(m -> out.add(m.get("name").asText()));
        return out;
    }

    private Module module(String name, int studyYear, boolean published) {
        return Module.builder()
            .studyYear(studyYear)
            .name(name)
            .position(moduleRepository.maxPosition(studyYear) + 1)
            .published(published)
            .build();
    }

    private Course course(Module module, String name, int position, boolean published) {
        return Course.builder().module(module).name(name).position(position).published(published).build();
    }

    /** Minimal valid PNG header + IHDR fragment — enough to pass magic-byte sniffing. */
    private byte[] pngBytes() {
        return new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 13, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0
        };
    }

    @TestConfiguration
    static class StorageTestConfig {
        @Bean
        @Primary
        RecordingMediaStorage recordingMediaStorage() {
            return new RecordingMediaStorage();
        }
    }

    static class RecordingMediaStorage implements MediaStorage {
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
