package com.docforge.template;

import com.docforge.AbstractIT;
import com.docforge.template.dto.TemplateListItemResponse;
import com.docforge.template.model.TemplateRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BUG-044 — the «за неделю» counter on the home page relies on
 * {@code GET /api/templates?createdAfter=…} filtering templates by
 * {@code template_registry.created_at >= createdAfter}.
 * <p>
 * The filter is a JPA {@code Specification}, so a Mockito unit test would only
 * assert that <em>some</em> predicate object is passed to the repository — it
 * could not prove that {@code created_at} is actually filtered. This test uses
 * the real PostgreSQL instance ({@link AbstractIT}) and saves two registries
 * directly through the repository:
 * <ul>
 *   <li>{@code createdAt} is {@code @Column(updatable = false)}, but it is still
 *       written on INSERT, so a repository {@code save()} of a fresh entity with
 *       an explicit old {@code createdAt} is enough — no native UPDATE needed;</li>
 *   <li>both rows share a random {@code category}, so {@code count(*)} style
 *       assertions stay isolated from the rest of the dev database.</li>
 * </ul>
 * The HTTP test additionally proves that the query parameter is bound
 * ({@code @DateTimeFormat(iso = DATE_TIME)} on an {@link OffsetDateTime}) and
 * forwarded to the service.
 */
@AutoConfigureMockMvc
class TemplateListCreatedAfterIT extends AbstractIT {

    @Autowired
    private TemplateService templateService;

    @Autowired
    private TemplateRegistryRepository registryRepository;

    @Autowired
    private MockMvc mockMvc;

    private final List<UUID> createdIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        registryRepository.deleteAllById(createdIds);
        createdIds.clear();
    }

    private TemplateRegistry saveRegistry(String name, String category, OffsetDateTime createdAt) {
        TemplateRegistry registry = new TemplateRegistry();
        registry.setId(UUID.randomUUID());
        registry.setName(name);
        registry.setCategory(category);
        registry.setCreatedAt(createdAt);
        registry.setUpdatedAt(createdAt);
        TemplateRegistry saved = registryRepository.save(registry);
        createdIds.add(saved.getId());
        return saved;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Service level: the Specification is applied
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("BUG-044: list(createdAfter) returns only templates created on/after the boundary")
    void list_givenCreatedAfter_excludesOlderTemplates() {
        // given — one template from last month, one from today, same category
        String category = "bug044-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        TemplateRegistry oldTemplate = saveRegistry("BUG-044 Old", category, now.minusDays(30));
        TemplateRegistry freshTemplate = saveRegistry("BUG-044 Fresh", category, now);

        // when
        Page<TemplateListItemResponse> page =
                templateService.list(0, 50, category, null, now.minusDays(6));

        // then — only the fresh one survives the created_at >= boundary filter
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent())
                .extracting(TemplateListItemResponse::id)
                .containsExactly(freshTemplate.getId());
        assertThat(page.getContent())
                .extracting(TemplateListItemResponse::name)
                .doesNotContain(oldTemplate.getName());

        // and — without the boundary both rows are visible (proves the filter,
        // not something else, removed the old template)
        Page<TemplateListItemResponse> unfiltered =
                templateService.list(0, 50, category, null, null);
        assertThat(unfiltered.getTotalElements()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("BUG-044: created_at >= boundary is inclusive — only the exact-boundary row is returned")
    void list_givenExactBoundary_includesOnlyRowExactlyOnBoundary() {
        // given — the count(*) on the dashboard must include a template created
        // in the same millisecond as the boundary, and must exclude one that is
        // 1 ms older. TIMESTAMPTZ has microsecond precision, so the boundary is
        // truncated to micros to survive the Java → Postgres → Java round-trip.
        String category = "bug044-" + UUID.randomUUID();
        OffsetDateTime boundary = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        TemplateRegistry onBoundary = saveRegistry("BUG-044 On Boundary", category, boundary);
        TemplateRegistry justBefore =
                saveRegistry("BUG-044 Just Before", category, boundary.minusNanos(1_000_000));

        // when
        Page<TemplateListItemResponse> page =
                templateService.list(0, 50, category, null, boundary);

        // then — inclusive lower bound: exactly the row with createdAt == boundary
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent())
                .extracting(TemplateListItemResponse::id)
                .containsExactly(onBoundary.getId());
        assertThat(page.getContent())
                .extracting(TemplateListItemResponse::id)
                .doesNotContain(justBefore.getId());

        // and — both rows exist (isolation sanity check for the category filter)
        assertThat(templateService.list(0, 50, category, null, null).getTotalElements())
                .isEqualTo(2);

        // and — moving the boundary 1 ms earlier lets the older row in, which
        // proves the exclusion above was caused by the boundary alone
        assertThat(templateService.list(0, 50, category, null, boundary.minusNanos(1_000_000))
                .getTotalElements()).isEqualTo(2);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  HTTP level: the query parameter binds and is forwarded
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("BUG-044: GET /api/templates?createdAfter=… binds ISO date-time and filters")
    void getTemplates_givenCreatedAfterParam_filtersAtHttpLevel() throws Exception {
        // given
        String category = "bug044-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        saveRegistry("BUG-044 HTTP Old", category, now.minusDays(30));
        TemplateRegistry freshTemplate =
                saveRegistry("BUG-044 HTTP Fresh", category, now);

        // when / then
        mockMvc.perform(get("/api/templates")
                        .param("page", "0")
                        .param("size", "20")
                        .param("category", category)
                        .param("createdAfter", now.minusDays(6).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(freshTemplate.getId().toString()))
                .andExpect(jsonPath("$.content[0].name").value("BUG-044 HTTP Fresh"));
    }

    /**
     * The browser sends the boundary as {@code Date.toISOString()}, i.e. always
     * UTC with a {@code Z} suffix — e.g. {@code 2026-10-19T00:00:00.000Z}.
     * This case pins that exact format (it is what the weekly counter uses).
     */
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("BUG-044: GET /api/templates?createdAfter=<literal UTC Z> is accepted and filters")
    void getTemplates_givenUtcZuluParam_filtersAtHttpLevel() throws Exception {
        // given — a fixed literal in the browser format, with rows around it
        String boundary = "2026-10-19T00:00:00.000Z";
        OffsetDateTime boundaryInstant = OffsetDateTime.parse(boundary);
        String category = "bug044-" + UUID.randomUUID();
        saveRegistry("BUG-044 Zulu Old", category, boundaryInstant.minusDays(1));
        TemplateRegistry freshTemplate =
                saveRegistry("BUG-044 Zulu Fresh", category, boundaryInstant.plusDays(1));

        // when / then
        mockMvc.perform(get("/api/templates")
                        .param("page", "0")
                        .param("size", "20")
                        .param("category", category)
                        .param("createdAfter", boundary))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(freshTemplate.getId().toString()))
                .andExpect(jsonPath("$.content[0].name").value("BUG-044 Zulu Fresh"));
    }

    /**
     * A malformed date is a client error: before the
     * {@code TypeMismatchException} handler it bubbled into the generic
     * {@code Exception} handler and produced HTTP 500.
     */
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("BUG-044: malformed createdAfter returns 400 with parameter=createdAfter")
    void getTemplates_givenMalformedCreatedAfterParam_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/templates")
                        .param("createdAfter", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.parameter").value("createdAfter"));
    }
}
