package com.vetspace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The request id has to be on <em>every</em> response, especially the failures — those
 * are the ones a student reports. Checking only the happy path would miss the case that
 * matters: the filter sitting inside the security chain instead of ahead of it, so 401s
 * come back with no id at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class RequestIdFilterIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("app.recaptcha.enabled", () -> false);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void successfulResponseCarriesARequestId() throws Exception {
        mockMvc.perform(get("/api/ping"))
            .andExpect(status().isOk())
            .andExpect(header().exists(RequestIdFilter.HEADER));
    }

    @Test
    void unauthorisedResponseCarriesARequestId() throws Exception {
        // The filter must sit ahead of the security chain: a 401 with no id is exactly
        // the report ("it said I was logged out") that cannot then be traced.
        mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().exists(RequestIdFilter.HEADER));
    }

    @Test
    void errorBodyRepeatsTheHeaderValueSoTheUserCanQuoteIt() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andReturn();

        String header = result.getResponse().getHeader(RequestIdFilter.HEADER);
        String body = com.jayway.jsonpath.JsonPath.read(
            result.getResponse().getContentAsString(), "$.requestId");
        // If these two ever diverge, the id a student reads off the toast points at a
        // different request than the one that failed.
        assertThat(body).isEqualTo(header);
    }

    @Test
    void aClientSuppliedIdIsEchoedBackSoATraceSurvivesAProxy() throws Exception {
        mockMvc.perform(get("/api/ping").header(RequestIdFilter.HEADER, "abc-123_XYZ"))
            .andExpect(status().isOk())
            .andExpect(header().string(RequestIdFilter.HEADER, "abc-123_XYZ"));
    }

    @Test
    void aHostileClientSuppliedIdIsReplacedRatherThanTrusted() throws Exception {
        // This value reaches the log file. Echoing it verbatim would let a caller inject
        // newlines and forge log lines, or bloat the log with a megabyte header.
        String nasty = "line1\nFAKE ERROR line2";
        MvcResult result = mockMvc.perform(get("/api/ping").header(RequestIdFilter.HEADER, nasty))
            .andExpect(status().isOk())
            .andReturn();

        String issued = result.getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(issued).isNotNull().doesNotContain("\n").doesNotContain("FAKE");
        assertThat(issued).matches("[A-Za-z0-9_-]{1,64}");
    }

    @Test
    void oversizedIdIsReplaced() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ping")
                .header(RequestIdFilter.HEADER, "x".repeat(500)))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(result.getResponse().getHeader(RequestIdFilter.HEADER)).hasSizeLessThanOrEqualTo(64);
    }

    @Test
    void eachRequestGetsItsOwnId() throws Exception {
        String first = mockMvc.perform(get("/api/ping")).andReturn()
            .getResponse().getHeader(RequestIdFilter.HEADER);
        String second = mockMvc.perform(get("/api/ping")).andReturn()
            .getResponse().getHeader(RequestIdFilter.HEADER);
        // A leaked MDC value from a pooled thread would make these identical, and the
        // log would then attribute both requests to one id.
        assertThat(first).isNotEqualTo(second);
    }
}
