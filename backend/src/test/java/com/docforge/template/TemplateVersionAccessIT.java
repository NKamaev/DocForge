package com.docforge.template;

import com.docforge.AbstractIT;
import com.docforge.AuditTestSupport;
import com.docforge.TestAuth;
import com.docforge.document.DocumentPermissionService;
import com.docforge.document.DocumentService;
import com.docforge.document.model.DocumentRole;
import com.docforge.security.JwtTokenProvider;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.SaveVersionRequest;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.dto.TemplateVersionDTO;
import com.docforge.template.dto.UpdateTemplateRequest;
import com.docforge.user.User;
import com.docforge.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the unified <em>published</em> semantics of the template
 * versions (BUG-059).
 *
 * <p>The rules under test:</p>
 * <ol>
 *   <li><strong>Rollback guard</strong> — a published template cannot be rolled back to a
 *       <em>draft</em> version: the live template is visible to everybody while
 *       {@code published = true}, so a rollback would expose draft content. The service
 *       throws {@code BusinessException} (→ {@code 409 Conflict} in
 *       {@code GlobalExceptionHandler}) and the live body must stay untouched;</li>
 *   <li><strong>Legacy Mongo-only snapshots</strong> — a version that exists only as a
 *       {@code template_snapshots} document (no row in {@code template_version}) was created
 *       by a publication, so it <em>exists</em> and is <em>published</em>: it is readable by
 *       anybody and can be instantiated (pinned) by anybody;</li>
 *   <li><strong>The exception is narrow</strong> — access to a document pinned to version A
 *       opens version A only; any other draft version B of the same template stays
 *       forbidden;</li>
 *   <li><strong>ROLLBACK of a published version stays published</strong> — it inherits the
 *       published status of its source by provenance, so a non-owner sees it in the version
 *       list and may read the snapshot;</li>
 *   <li><strong>Diff with an unknown version</strong> → {@code 404}, not {@code 403}: the
 *       version simply does not exist.</li>
 * </ol>
 *
 * <p>Exercises the real security chain (JWT → {@code JwtAuthFilter} →
 * {@code UserDetailsServiceImpl}), the real {@code TemplateService}, the real
 * {@code DocumentService} and the real {@code DocumentPermissionService}. Three identities
 * are used: {@code userA} (the template owner), {@code userB} (a stranger) and the seeded
 * {@code admin} account.</p>
 */
@AutoConfigureMockMvc
class TemplateVersionAccessIT extends AbstractIT {

    /** {@code TemplateServiceImpl.requireVersionVisible} — draft version of a stranger. */
    private static final String VERSION_DENIED = "Нет доступа к версии шаблона";

    /** {@code TemplateServiceImpl.rollback} — rollback of a published template to a draft. */
    private static final String ROLLBACK_DENIED = "Нельзя откатить опубликованный шаблон к черновой версии";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private TemplateService templateService;
    @Autowired private DocumentService documentService;
    @Autowired private DocumentPermissionService documentPermissionService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MongoTemplate mongoTemplate;

    private String suffix;

    /** Owner of the test template. */
    private UUID userAId;
    private String userALogin;
    private String userAToken;

    /** Stranger — owns nothing. */
    private UUID userBId;
    private String userBToken;

    /** Seeded administrator ({@code V014__default_admin_user.sql}). */
    private String adminToken;

    /** Published template owned by {@code userA}. */
    private UUID templateId;

    /** Version number of the {@code PUBLISH} version of {@link #templateId}. */
    private int publishedVersionNumber;

    /** {@code template_version.id} of the PUBLISH version (= the Mongo snapshot id). */
    private UUID publishedSnapshotId;

    /** Version number of the {@code MINOR} draft version of {@link #templateId}. */
    private int draftVersionNumber;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        String loginA = "tpl-ver-a-" + suffix;
        String loginB = "tpl-ver-b-" + suffix;

        userAId = createUser(loginA, "Template Version A");
        userBId = createUser(loginB, "Template Version B");
        userALogin = loginA;
        userAToken = jwtTokenProvider.generateToken(loginA);
        userBToken = jwtTokenProvider.generateToken(loginB);
        adminToken = jwtTokenProvider.generateToken("admin");

        // BUG-068: изменять шаблон (update/publish/saveVersion/rollback) может только
        // владелец или админ. Прямые вызовы сервиса выполняются в контексте
        // безопасности владельца шаблона.
        TestAuth.authenticateAs(userALogin);

        // The template is built in three steps, so that the versions carry different bodies:
        //   1) MINOR draft  → body "DRAFT BODY"   (a version nobody but the owner may see)
        //   2) live body    → "PUBLISHED BODY"   (what everybody reads)
        //   3) publish      → the PUBLISH version carries "PUBLISHED BODY"
        TemplateResponse created = templateService.create(
                userAId,
                new CreateTemplateRequest("BUG-059 Versions " + suffix, null, category(), null));
        templateId = created.id();
        assertThat(created.published()).isFalse();

        templateService.update(templateId, updateRequest(body("DRAFT BODY")));
        draftVersionNumber = templateService
                .saveVersion(templateId, new SaveVersionRequest("MINOR", "черновик BUG-059"))
                .version()
                .versionNumber();

        templateService.update(templateId, updateRequest(body("PUBLISHED BODY")));
        assertThat(templateService.publish(templateId).published()).isTrue();

        TemplateVersionDTO publishVersion = templateService.listVersions(templateId).stream()
                .filter(v -> "PUBLISH".equals(v.bumpType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("PUBLISH version was not created"));
        publishedVersionNumber = publishVersion.versionNumber();
        publishedSnapshotId = publishVersion.id();
        assertThat(publishedVersionNumber).isGreaterThan(draftVersionNumber);
        TestAuth.clear();
    }

    // ── 1. rollback guard ─────────────────────────────────────────────────────

    @Test
    @DisplayName("rollback of a published template to a MINOR draft → 409 and the live body stays untouched")
    void rollback_publishedTemplateToDraft_returns409AndKeepsLiveBody() throws Exception {
        // when — the owner tries to roll the published template back to the draft version.
        mockMvc.perform(post("/api/templates/{id}/rollback/{v}", templateId, draftVersionNumber)
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(containsString(ROLLBACK_DENIED)));

        // then — the live template still carries the published body, not the draft one.
        mockMvc.perform(get("/api/templates/{id}", templateId)
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.body.content[0].content[0].text").value("PUBLISHED BODY"));

        // … and the draft was not published to the world either.
        mockMvc.perform(get("/api/templates/{id}", templateId)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.content[0].content[0].text").value("PUBLISHED BODY"));
    }

    @Test
    @DisplayName("rollback of a published template to a MINOR draft → no new version is created")
    void rollback_publishedTemplateToDraft_createsNoVersion() throws Exception {
        int versionsBefore = templateService.listVersions(templateId).size();

        mockMvc.perform(post("/api/templates/{id}/rollback/{v}", templateId, draftVersionNumber)
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isConflict());

        assertThat(templateService.listVersions(templateId)).hasSize(versionsBefore);
    }

    // ── 2. legacy Mongo-only snapshot ─────────────────────────────────────────

    @Test
    @DisplayName("legacy snapshot without a template_version row → a stranger may read the version → 200")
    void getVersion_legacyMongoOnlySnapshot_readableByStranger() throws Exception {
        // given — the PUBLISH row in PostgreSQL is gone, the MongoDB snapshot is still there
        // (the shape of a template published before versioning existed).
        deleteVersionRow(publishedVersionNumber);
        assertThat(versionRowCount(publishedVersionNumber)).isZero();
        assertThat(mongoSnapshotCount()).isPositive();

        // when — a stranger reads that version.
        mockMvc.perform(get("/api/templates/{id}/versions/{v}", templateId, publishedVersionNumber)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value(templateId.toString()))
                .andExpect(jsonPath("$.versionNumber").value(publishedVersionNumber));
    }

    @Test
    @DisplayName("legacy snapshot without a template_version row → a stranger may instantiate it → 201 pinned to that version")
    void createDocument_legacyMongoOnlySnapshot_createsPinnedDocument() throws Exception {
        // given — the same legacy shape: only the MongoDB snapshot survives.
        deleteVersionRow(publishedVersionNumber);

        // when — a stranger creates a document pinned to that version.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userBToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Legacy Version Doc " + suffix, publishedVersionNumber)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.templateId").value(templateId.toString()))
                .andExpect(jsonPath("$.templateVersionNumber").value(publishedVersionNumber))
                .andExpect(jsonPath("$.templateSnapshotId").value(publishedSnapshotId.toString()));
    }

    // ── 3. the document exception is narrow ───────────────────────────────────

    @Test
    @DisplayName("a collaborator of a document pinned to version A → 200 for A, but 403 for another draft version B")
    void getVersion_documentCollaborator_draftExceptionIsNarrow() throws Exception {
        // given — a second draft version B, and a document pinned to version A that is
        // shared with the stranger.
        int draftVersionB = TestAuth.callAs(userALogin, () -> templateService
                .saveVersion(templateId, new SaveVersionRequest("MINOR", "черновик B"))
                .version()
                .versionNumber());
        assertThat(draftVersionB).isGreaterThan(draftVersionNumber);
        assertThat(draftVersionB).isNotEqualTo(publishedVersionNumber);

        UUID documentId = documentService.create(
                templateId,
                "BUG-059 Pinned Doc " + suffix,
                userAId,
                null,
                draftVersionNumber).id();
        documentPermissionService.grantRole(documentId, userBId, DocumentRole.VIEWER, userAId);
        assertThat(documentPermissionService.getEffectiveRole(documentId, userBId))
                .isEqualTo(DocumentRole.VIEWER);

        // when — the collaborator reads the version the document is pinned to.
        mockMvc.perform(get("/api/templates/{id}/versions/{v}", templateId, draftVersionNumber)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(draftVersionNumber));

        // … but another draft version of the same template stays forbidden.
        mockMvc.perform(get("/api/templates/{id}/versions/{v}", templateId, draftVersionB)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(VERSION_DENIED));
    }

    // ── 4. ROLLBACK of a published version stays published ────────────────────

    @Test
    @DisplayName("ROLLBACK of a published version → visible to a non-owner in the version list and readable → 200")
    void listVersions_rollbackOfPublishedVersion_visibleToStranger() throws Exception {
        // given — the owner rolls the template back to the published version; the rollback
        // creates a new version (bumpType = "ROLLBACK", rollback_from = the published one).
        int rollbackVersionNumber = TestAuth.callAs(userALogin, () -> templateService
                .rollback(templateId, publishedVersionNumber)
                .version()
                .versionNumber());
        assertThat(rollbackVersionNumber).isGreaterThan(publishedVersionNumber);

        // when — a stranger lists the versions.
        mockMvc.perform(get("/api/templates/{id}/versions", templateId)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].versionNumber", hasItem(publishedVersionNumber)))
                .andExpect(jsonPath("$[*].versionNumber", hasItem(rollbackVersionNumber)));

        // … and reads the rollback snapshot.
        mockMvc.perform(get("/api/templates/{id}/versions/{v}", templateId, rollbackVersionNumber)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value(templateId.toString()))
                .andExpect(jsonPath("$.versionNumber").value(rollbackVersionNumber));
    }

    @Test
    @DisplayName("ROLLBACK of a published version → the draft version stays invisible to a non-owner")
    void listVersions_rollbackOfPublishedVersion_draftStillHiddenFromStranger() throws Exception {
        TestAuth.runAs(userALogin, () -> templateService.rollback(templateId, publishedVersionNumber));

        mockMvc.perform(get("/api/templates/{id}/versions", templateId)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].versionNumber", not(hasItem(draftVersionNumber))));
    }

    // ── 5. diff with an unknown version ───────────────────────────────────────

    @Test
    @DisplayName("diff with a non-existent version → 404 «Снимок шаблона не найден» (stranger and owner alike)")
    void diff_nonexistentVersion_returns404() throws Exception {
        int missingVersion = publishedVersionNumber + 777;

        // when — a stranger compares a published version with a version that does not exist.
        mockMvc.perform(get("/api/templates/{id}/versions/diff", templateId)
                        .param("from", String.valueOf(publishedVersionNumber))
                        .param("to", String.valueOf(missingVersion))
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("Снимок шаблона не найден")));

        // … and the same for the owner — an unknown version is a 404, not a 403.
        mockMvc.perform(get("/api/templates/{id}/versions/diff", templateId)
                        .param("from", String.valueOf(publishedVersionNumber))
                        .param("to", String.valueOf(missingVersion))
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("Снимок шаблона не найден")));

        // … and for the administrator.
        mockMvc.perform(get("/api/templates/{id}/versions/diff", templateId)
                        .param("from", String.valueOf(publishedVersionNumber))
                        .param("to", String.valueOf(missingVersion))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** Unique category of the test template — isolates it from the dev database. */
    private String category() {
        return "bug059-ver-" + suffix;
    }

    /** TipTap body with a single paragraph. */
    private static Map<String, Object> body(String text) {
        return Map.of(
                "type", "doc",
                "content", List.of(Map.of(
                        "type", "paragraph",
                        "content", List.of(Map.of(
                                "type", "text",
                                "text", text)))));
    }

    private UpdateTemplateRequest updateRequest(Map<String, Object> body) {
        return new UpdateTemplateRequest(
                "BUG-059 Versions " + suffix, null, category(), null, null, body, null, null, null, null);
    }

    /**
     * Deletes the {@code template_version} row of the given version — the MongoDB snapshot
     * stays untouched, so the version becomes a “legacy” one (published before the
     * {@code template_version} table existed).
     */
    private void deleteVersionRow(int versionNumber) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM template_version WHERE template_id = ? AND version_number = ?",
                templateId, versionNumber);
        assertThat(deleted).isEqualTo(1);
    }

    /** Number of {@code template_version} rows of the given version. */
    private int versionRowCount(int versionNumber) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM template_version WHERE template_id = ? AND version_number = ?",
                Integer.class, templateId, versionNumber);
        return count == null ? 0 : count;
    }

    /** Number of MongoDB snapshots of the test template. */
    private long mongoSnapshotCount() {
        return mongoTemplate.count(
                Query.query(Criteria.where("template_id").is(templateId)), "template_snapshots");
    }

    private UUID createUser(String login, String displayName) {
        User user = new User();
        user.setLogin(login);
        user.setPasswordHash("hash");
        user.setDisplayName(displayName);
        return userRepository.save(user).getId();
    }

    private static String createBody(UUID templateId, String name, Integer versionNumber) {
        return String.format(
                "{\"templateId\":\"%s\",\"name\":\"%s\",\"folderId\":null,\"templateVersionNumber\":%s}",
                templateId,
                name,
                versionNumber == null ? "null" : versionNumber);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * Cleanup of the shared dev database: documents first (FK to the template), then the
     * template (FK cascade to {@code template_version}), then the users. Every statement is
     * scoped to the ids created by this test; failures are <em>not</em> swallowed — a broken
     * cleanup must fail the test, otherwise leftovers silently distort the next runs.
     *
     * <p>The audit writer is asynchronous ({@code @Async}), so its rows can land
     * <em>after</em> the business call has returned. Deleting the users while such a row
     * is on its way breaks the FK {@code audit_log → users}. The cleanup therefore removes
     * every user together with its audit rows through
     * {@link AuditTestSupport#deleteUserWithAuditRows}, which retries the deletion when a
     * late audit row blocks it — the cleanup becomes deterministic.</p>
     */
    @AfterEach
    void cleanUp() {
        TestAuth.clear();
        if (templateId != null) {
            mongoTemplate.remove(Query.query(Criteria.where("template_id").is(templateId)), "documents");
            mongoTemplate.remove(Query.query(Criteria.where("template_id").is(templateId)), "template_snapshots");
            mongoTemplate.remove(Query.query(Criteria.where("_id").is(templateId)), "templates");

            jdbcTemplate.update("DELETE FROM document_registry WHERE template_id = ?", templateId);
            jdbcTemplate.update("DELETE FROM template_version WHERE template_id = ?", templateId);
            jdbcTemplate.update("DELETE FROM template_registry WHERE id = ?", templateId);
            jdbcTemplate.update("DELETE FROM audit_log WHERE entity_id = ?", templateId);
        }
        for (UUID userId : List.of(userAId, userBId)) {
            if (userId == null) continue;
            // Аудит асинхронный (@Async) — удаляем пользователя вместе с его строками
            // аудита детерминированно (retry при поздней строке аудита).
            AuditTestSupport.deleteUserWithAuditRows(jdbcTemplate, userId);
        }
    }
}
