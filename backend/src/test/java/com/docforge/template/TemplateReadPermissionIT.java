package com.docforge.template;

import com.docforge.AbstractIT;
import com.docforge.document.DocumentPermissionService;
import com.docforge.document.DocumentService;
import com.docforge.document.model.DocumentRole;
import com.docforge.security.JwtTokenProvider;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.SaveVersionRequest;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.dto.TemplateVersionDTO;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the ownership scoping of the template read endpoints
 * (fix for BUG-059).
 *
 * <p>Before the fix any authenticated user could read <em>someone else's</em> unpublished
 * (draft) template, browse its draft versions and diff them. The read endpoints of the
 * {@code template} module are now ownership-scoped:</p>
 * <ol>
 *   <li>{@code GET /api/templates/{id}} — a published template is readable by everybody;
 *       a draft one only by its owner or an administrator
 *       ({@code 403 «Нет доступа к шаблону»} otherwise);</li>
 *   <li>{@code GET /api/templates/{id}/versions} — the owner/administrator sees all versions;
 *       everybody else sees only the <em>published</em> ones ({@code bumpType = PUBLISH},
 *       legacy {@code NULL} or a {@code ROLLBACK} of a published version) — draft versions
 *       must not show up in the list at all;</li>
 *   <li>{@code GET /api/templates/{id}/versions/{versionNumber}} — the owner/administrator may
 *       read any version; everybody else only published ones
 *       ({@code 403 «Нет доступа к версии шаблона»} otherwise). The single exception: a user
 *       who has access to a document pinned to that version may read the draft snapshot;</li>
 *   <li>{@code GET /api/templates} — a non-administrator sees published templates plus their
 *       own drafts; drafts of other users are filtered out at query level; administrators see
 *       everything; the {@code ?published=true} filter behaves as before;</li>
 *   <li>{@code GET /api/templates/{id}/versions/diff?from=&to=} — <em>both</em> versions must
 *       be readable by the caller, otherwise {@code 403}.</li>
 * </ol>
 *
 * <p>Exercises the real security chain (JWT → {@code JwtAuthFilter} →
 * {@code UserDetailsServiceImpl} → {@code TemplateOwnerResolver}), the real
 * {@code TemplateService} (versioning included) and the real {@code DocumentService} /
 * {@code DocumentPermissionService}. Three identities are used: {@code userA} (the template
 * owner), {@code userB} (a stranger) and the seeded {@code admin} account
 * ({@code V014__default_admin_user.sql}) — the administrator is authenticated with a real
 * JWT as well, so {@code ROLE_ADMIN} is resolved by {@code UserDetailsServiceImpl}.</p>
 */
@AutoConfigureMockMvc
class TemplateReadPermissionIT extends AbstractIT {

    /** {@code TemplateServiceImpl.getReadable} — draft template of a stranger. */
    private static final String TEMPLATE_DENIED = "Нет доступа к шаблону";

    /** {@code TemplateServiceImpl.requireVersionVisible} — draft version of a stranger. */
    private static final String VERSION_DENIED = "Нет доступа к версии шаблона";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private TemplateService templateService;
    @Autowired private DocumentService documentService;
    @Autowired private DocumentPermissionService documentPermissionService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MongoTemplate mongoTemplate;

    private String suffix;

    /** Owner of both test templates. */
    private UUID userAId;
    private String userAToken;

    /** Stranger — owns nothing. */
    private UUID userBId;
    private String userBToken;

    /** Seeded administrator ({@code V014__default_admin_user.sql}). */
    private String adminToken;

    /** Unpublished (draft) template owned by {@code userA}. */
    private UUID draftTemplateId;

    /** Published template owned by {@code userA} — carries a published and a draft version. */
    private UUID publishedTemplateId;

    /** Version number of the {@code PUBLISH} version of {@link #publishedTemplateId}. */
    private int publishedVersionNumber;

    /** Version number of the {@code MINOR} draft version of {@link #publishedTemplateId}. */
    private int draftVersionNumber;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        String loginA = "tpl-read-a-" + suffix;
        String loginB = "tpl-read-b-" + suffix;

        userAId = createUser(loginA, "Template Read A");
        userBId = createUser(loginB, "Template Read B");
        userAToken = jwtTokenProvider.generateToken(loginA);
        userBToken = jwtTokenProvider.generateToken(loginB);
        // The administrator is a seeded account — its JWT is issued the same way, so the
        // real UserDetailsServiceImpl grants ROLE_ADMIN.
        adminToken = jwtTokenProvider.generateToken("admin");

        // 1) A draft template — never published, so only its owner (and admins) may read it.
        TemplateResponse draft = templateService.create(
                userAId,
                new CreateTemplateRequest("BUG-059 Draft " + suffix, null, category(), null));
        draftTemplateId = draft.id();
        assertThat(draft.published()).isFalse();

        // 2) A published template with one PUBLISH version and one MINOR draft version.
        TemplateResponse published = templateService.create(
                userAId,
                new CreateTemplateRequest("BUG-059 Published " + suffix, null, category(), null));
        publishedTemplateId = published.id();
        assertThat(templateService.publish(publishedTemplateId).published()).isTrue();

        publishedVersionNumber = templateService.listVersions(publishedTemplateId).stream()
                .filter(v -> "PUBLISH".equals(v.bumpType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("PUBLISH version was not created"))
                .versionNumber();

        draftVersionNumber = templateService
                .saveVersion(publishedTemplateId, new SaveVersionRequest("MINOR", "черновик BUG-059"))
                .version()
                .versionNumber();
        assertThat(draftVersionNumber).isGreaterThan(publishedVersionNumber);
    }

    // ── 1. GET /api/templates/{id} — draft template ───────────────────────────

    @Test
    @DisplayName("draft template: owner → 200, stranger → 403 «Нет доступа к шаблону», admin → 200")
    void getById_draftTemplate_ownerAllowed_strangerDenied_adminAllowed() throws Exception {
        // when — the owner reads their own draft
        mockMvc.perform(get("/api/templates/{id}", draftTemplateId)
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(draftTemplateId.toString()))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.ownerId").value(userAId.toString()));

        // when — a stranger reads somebody else's draft
        mockMvc.perform(get("/api/templates/{id}", draftTemplateId)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(TEMPLATE_DENIED));

        // when — the administrator reads the draft
        mockMvc.perform(get("/api/templates/{id}", draftTemplateId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(draftTemplateId.toString()));
    }

    // ── 2. GET /api/templates/{id} — published template ────────────────────────

    @Test
    @DisplayName("published template: a stranger may read it → 200")
    void getById_publishedTemplate_strangerAllowed() throws Exception {
        mockMvc.perform(get("/api/templates/{id}", publishedTemplateId)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publishedTemplateId.toString()))
                .andExpect(jsonPath("$.published").value(true));
    }

    // ── 3. GET /api/templates/{id}/versions — the list ─────────────────────────

    @Test
    @DisplayName("versions list: the owner sees the draft version, a stranger does not")
    void listVersions_draftVersion_visibleToOwnerOnly() throws Exception {
        // when — the owner lists the versions
        mockMvc.perform(get("/api/templates/{id}/versions", publishedTemplateId)
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].versionNumber", hasItem(publishedVersionNumber)))
                .andExpect(jsonPath("$[*].versionNumber", hasItem(draftVersionNumber)));

        // when — a stranger lists the versions
        mockMvc.perform(get("/api/templates/{id}/versions", publishedTemplateId)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].versionNumber", hasItem(publishedVersionNumber)))
                // the draft version must not leak into the stranger's list
                .andExpect(jsonPath("$[*].versionNumber", not(hasItem(draftVersionNumber))));
    }

    @Test
    @DisplayName("versions list: the administrator sees the draft version")
    void listVersions_draftVersion_visibleToAdmin() throws Exception {
        mockMvc.perform(get("/api/templates/{id}/versions", publishedTemplateId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].versionNumber", hasItem(publishedVersionNumber)))
                .andExpect(jsonPath("$[*].versionNumber", hasItem(draftVersionNumber)));
    }

    // ── 4. GET /api/templates/{id}/versions/{versionNumber} — the snapshot ─────

    @Test
    @DisplayName("version snapshot: stranger → 403 «Нет доступа к версии шаблона» for the draft, 200 for the published version")
    void getVersion_draftVersion_deniedForStranger_publishedVersionAllowed() throws Exception {
        // when — a stranger reads the draft version
        mockMvc.perform(get("/api/templates/{id}/versions/{v}", publishedTemplateId, draftVersionNumber)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(VERSION_DENIED));

        // when — the same stranger reads the published version
        mockMvc.perform(get("/api/templates/{id}/versions/{v}", publishedTemplateId, publishedVersionNumber)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value(publishedTemplateId.toString()))
                .andExpect(jsonPath("$.versionNumber").value(publishedVersionNumber));
    }

    @Test
    @DisplayName("version snapshot: owner → 200 for the draft version, admin → 200")
    void getVersion_draftVersion_allowedForOwnerAndAdmin() throws Exception {
        // when — the owner reads their own draft version
        mockMvc.perform(get("/api/templates/{id}/versions/{v}", publishedTemplateId, draftVersionNumber)
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value(publishedTemplateId.toString()))
                .andExpect(jsonPath("$.versionNumber").value(draftVersionNumber));

        // when — the administrator reads the draft version
        mockMvc.perform(get("/api/templates/{id}/versions/{v}", publishedTemplateId, draftVersionNumber)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(draftVersionNumber));
    }

    // ── 5. exception: a collaborator of a document pinned to the draft ─────────

    @Test
    @DisplayName("version snapshot: a user with access to a document pinned to the draft version → 200")
    void getVersion_draftVersion_allowedForDocumentCollaborator() throws Exception {
        // given — the owner creates a document pinned to the draft version and shares it
        // with the stranger.
        UUID documentId = documentService.create(
                publishedTemplateId,
                "BUG-059 Pinned Doc " + suffix,
                userAId,
                null,
                draftVersionNumber).id();
        documentPermissionService.grantRole(documentId, userBId, DocumentRole.VIEWER, userAId);
        assertThat(documentPermissionService.getEffectiveRole(documentId, userBId))
                .isEqualTo(DocumentRole.VIEWER);

        // when — the collaborator reads the draft snapshot
        mockMvc.perform(get("/api/templates/{id}/versions/{v}", publishedTemplateId, draftVersionNumber)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value(publishedTemplateId.toString()))
                .andExpect(jsonPath("$.versionNumber").value(draftVersionNumber));
    }

    // ── 6. GET /api/templates — the list ──────────────────────────────────────

    @Test
    @DisplayName("list: a stranger sees the published template but not somebody else's draft")
    void list_stranger_seesPublishedButNotForeignDraft() throws Exception {
        mockMvc.perform(get("/api/templates")
                        .param("page", "0")
                        .param("size", "50")
                        .param("category", category())
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(publishedTemplateId.toString()))
                .andExpect(jsonPath("$.content[0].published").value(true));
    }

    @Test
    @DisplayName("list: the owner sees both the published template and their own draft")
    void list_owner_seesPublishedAndOwnDraft() throws Exception {
        mockMvc.perform(get("/api/templates")
                        .param("page", "0")
                        .param("size", "50")
                        .param("category", category())
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", hasItem(publishedTemplateId.toString())))
                .andExpect(jsonPath("$.content[*].id", hasItem(draftTemplateId.toString())));
    }

    @Test
    @DisplayName("list: the administrator sees both templates")
    void list_admin_seesEverything() throws Exception {
        mockMvc.perform(get("/api/templates")
                        .param("page", "0")
                        .param("size", "50")
                        .param("category", category())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", hasItem(publishedTemplateId.toString())))
                .andExpect(jsonPath("$.content[*].id", hasItem(draftTemplateId.toString())));
    }

    @Test
    @DisplayName("list: ?published=true keeps its behaviour — the draft is excluded for everybody")
    void list_publishedTrue_unchanged() throws Exception {
        mockMvc.perform(get("/api/templates")
                        .param("page", "0")
                        .param("size", "50")
                        .param("category", category())
                        .param("published", "true")
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(publishedTemplateId.toString()));

        mockMvc.perform(get("/api/templates")
                        .param("page", "0")
                        .param("size", "50")
                        .param("category", category())
                        .param("published", "true")
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(publishedTemplateId.toString()));

        mockMvc.perform(get("/api/templates")
                        .param("page", "0")
                        .param("size", "50")
                        .param("category", category())
                        .param("published", "true")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(publishedTemplateId.toString()));
    }

    // ── 7. GET /api/templates/{id}/versions/diff ──────────────────────────────

    @Test
    @DisplayName("diff: a stranger comparing a published version with a draft version → 403")
    void diff_draftVersionIncluded_deniedForStranger() throws Exception {
        mockMvc.perform(get("/api/templates/{id}/versions/diff", publishedTemplateId)
                        .param("from", String.valueOf(publishedVersionNumber))
                        .param("to", String.valueOf(draftVersionNumber))
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(VERSION_DENIED));
    }

    @Test
    @DisplayName("diff: the owner and the administrator may compare a published version with a draft version")
    void diff_draftVersionIncluded_allowedForOwnerAndAdmin() throws Exception {
        mockMvc.perform(get("/api/templates/{id}/versions/diff", publishedTemplateId)
                        .param("from", String.valueOf(publishedVersionNumber))
                        .param("to", String.valueOf(draftVersionNumber))
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasChanges").exists());

        mockMvc.perform(get("/api/templates/{id}/versions/diff", publishedTemplateId)
                        .param("from", String.valueOf(publishedVersionNumber))
                        .param("to", String.valueOf(draftVersionNumber))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasChanges").exists());
    }

    @Test
    @DisplayName("diff: a stranger comparing two published versions → 200")
    void diff_twoPublishedVersions_allowedForStranger() throws Exception {
        mockMvc.perform(get("/api/templates/{id}/versions/diff", publishedTemplateId)
                        .param("from", String.valueOf(publishedVersionNumber))
                        .param("to", String.valueOf(publishedVersionNumber))
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasChanges").exists());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** Unique category of the two test templates — isolates them from the dev database. */
    private String category() {
        return "bug059-" + suffix;
    }

    private UUID createUser(String login, String displayName) {
        User user = new User();
        user.setLogin(login);
        user.setPasswordHash("hash");
        user.setDisplayName(displayName);
        return userRepository.save(user).getId();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * Cleanup of the shared dev database: documents first (FK to the template), then the
     * template (FK cascade to {@code template_version}), then the users. Every statement is
     * scoped to the ids created by this test; failures are <em>not</em> swallowed — a broken
     * cleanup must fail the test, otherwise leftovers silently distort the next runs.
     */
    @AfterEach
    void cleanUp() {
        List<UUID> templateIds = List.of(draftTemplateId, publishedTemplateId);

        mongoTemplate.remove(Query.query(Criteria.where("template_id").in(templateIds)), "documents");
        mongoTemplate.remove(Query.query(Criteria.where("template_id").in(templateIds)), "template_snapshots");
        mongoTemplate.remove(Query.query(Criteria.where("_id").in(templateIds)), "templates");

        jdbcTemplate.update("DELETE FROM document_registry WHERE template_id IN (?, ?)",
                draftTemplateId, publishedTemplateId);
        jdbcTemplate.update("DELETE FROM template_version WHERE template_id IN (?, ?)",
                draftTemplateId, publishedTemplateId);
        jdbcTemplate.update("DELETE FROM template_registry WHERE id IN (?, ?)",
                draftTemplateId, publishedTemplateId);

        jdbcTemplate.update("DELETE FROM audit_log WHERE entity_id IN (?, ?)",
                draftTemplateId, publishedTemplateId);
        for (UUID userId : List.of(userAId, userBId)) {
            if (userId == null) continue;
            jdbcTemplate.update("DELETE FROM audit_log WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
    }
}
