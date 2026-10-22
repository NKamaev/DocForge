package com.docforge.document;

import com.docforge.AbstractIT;
import com.docforge.document.dto.FolderRequest;
import com.docforge.document.dto.FolderResponse;
import com.docforge.document.model.Document;
import com.docforge.document.model.FolderRole;
import com.docforge.security.JwtTokenProvider;
import com.docforge.template.TemplateService;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.user.User;
import com.docforge.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the access checks on {@code POST /api/documents}
 * (fix for BUG-052).
 *
 * <p>The endpoint used to let any authenticated user instantiate <em>someone else's
 * unpublished (draft) template</em> and drop the new document into <em>someone else's
 * folder</em>. The check now lives in {@code DocumentServiceImpl.create} (same behaviour,
 * single template load) and enforces:</p>
 * <ol>
 *   <li>the template must be published <strong>or</strong> owned by the caller
 *       ({@code 403 «Нет доступа к шаблону»} otherwise);</li>
 *   <li>when {@code folderId} is supplied the caller needs
 *       {@code FolderPermission.WRITE} on it ({@code 403 «Нет прав на папку-цель»}).</li>
 * </ol>
 *
 * <p>Missing entities surface as {@code 404}: an unknown {@code templateId}
 * ({@code «Шаблон не найден»}) and an unknown {@code folderId} ({@code «Папка не найдена»}).
 * Denied paths must not persist anything — neither in PostgreSQL
 * ({@code document_registry}) nor in MongoDB ({@code documents}).</p>
 *
 * <p>Exercises the real security chain (JWT → {@code JwtAuthFilter} →
 * {@code UserDetailsServiceImpl} → {@code DocumentOwnerResolver}), the real
 * {@code TemplateService} and the real {@code FolderPermissionService}. Two persisted
 * users are used: {@code userA} (the caller) and {@code userB} (the foreign owner).</p>
 */
@AutoConfigureMockMvc
class DocumentCreatePermissionIT extends AbstractIT {

    private static final String TEMPLATE_DENIED = "Нет доступа к шаблону";
    private static final String FOLDER_DENIED = "Нет прав на папку-цель";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private DocumentRegistryRepository documentRegistryRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private TemplateService templateService;
    @Autowired private FolderService folderService;
    @Autowired private FolderPermissionService folderPermissionService;

    private String suffix;

    /** Caller. */
    private UUID userAId;
    private String userAToken;

    /** Foreign owner of the draft template / foreign folder. */
    private UUID userBId;

    /** userB's unpublished (draft) template. */
    private UUID draftTemplateId;
    /** userB's published template. */
    private UUID publishedTemplateId;
    /** userB's folder. */
    private UUID userBFolderId;
    /** userA's own folder. */
    private UUID userAFolderId;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        String loginA = "create-perm-a-" + suffix;
        String loginB = "create-perm-b-" + suffix;

        userAId = createUser(loginA, "Create Perm A");
        userBId = createUser(loginB, "Create Perm B");
        userAToken = jwtTokenProvider.generateToken(loginA);

        // B owns a draft template and a published one.
        TemplateResponse draft = templateService.create(
                userBId,
                new CreateTemplateRequest("CreatePermIT Draft " + suffix, null, null, null));
        draftTemplateId = draft.id();
        assertThat(draft.published()).isFalse();

        TemplateResponse published = templateService.create(
                userBId,
                new CreateTemplateRequest("CreatePermIT Published " + suffix, null, null, null));
        publishedTemplateId = templateService.publish(published.id()).id();

        // B owns a folder; A owns another one.
        FolderResponse folderB = folderService.create(
                userBId, new FolderRequest("CreatePermIT B Folder " + suffix, null, null));
        userBFolderId = folderB.id();

        FolderResponse folderA = folderService.create(
                userAId, new FolderRequest("CreatePermIT A Folder " + suffix, null, null));
        userAFolderId = folderA.id();
    }

    // ── template gate ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("non-owner + unpublished template → 403 and no document_registry row created")
    void create_foreignUnpublishedTemplate_returns403AndNoRow() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(draftTemplateId, "Illegal Draft Doc " + suffix, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(TEMPLATE_DENIED));

        // The service must reject before anything is persisted.
        assertThat(documentRegistryRepository.existsByTemplateId(draftTemplateId)).isFalse();
        assertThat(countMongoDocsForTemplate(draftTemplateId)).isZero();
    }

    @Test
    @DisplayName("owner + own unpublished template → 201")
    void create_ownUnpublishedTemplate_returns201() throws Exception {
        // userA instantiates userB's draft template — denied above. Here userA owns the draft.
        TemplateResponse ownDraft = templateService.create(
                userAId,
                new CreateTemplateRequest("CreatePermIT A Draft " + suffix, null, null, null));
        assertThat(ownDraft.published()).isFalse();

        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(ownDraft.id(), "Own Draft Doc " + suffix, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Own Draft Doc " + suffix));

        assertThat(documentRegistryRepository.existsByTemplateId(ownDraft.id())).isTrue();
    }

    @Test
    @DisplayName("another user + published template → 201")
    void create_foreignPublishedTemplate_returns201() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(publishedTemplateId, "Published Doc " + suffix, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Published Doc " + suffix));

        assertThat(documentRegistryRepository.existsByTemplateId(publishedTemplateId)).isTrue();
    }

    // ── folder gate ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("non-owner + foreign folder → 403 and no document_registry row created")
    void create_foreignFolder_returns403AndNoRow() throws Exception {
        // Published template → the template gate passes; the folder gate must deny.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(publishedTemplateId, "Foreign Folder Doc " + suffix, userBFolderId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(FOLDER_DENIED));

        assertThat(documentRegistryRepository.existsByTemplateId(publishedTemplateId)).isFalse();
        assertThat(countMongoDocsForTemplate(publishedTemplateId)).isZero();
    }

    @Test
    @DisplayName("owner + own folder → 201")
    void create_ownFolder_returns201() throws Exception {
        TemplateResponse ownDraft = templateService.create(
                userAId,
                new CreateTemplateRequest("CreatePermIT A Draft Folder " + suffix, null, null, null));

        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(ownDraft.id(), "Own Folder Doc " + suffix, userAFolderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Own Folder Doc " + suffix));

        assertThat(documentRegistryRepository.existsByTemplateId(ownDraft.id())).isTrue();
    }

    // ── folder gate: explicit role grants ─────────────────────────────────────

    @Test
    @DisplayName("VIEWER role on foreign folder → 403 «Нет прав на папку-цель» and nothing persisted")
    void create_foreignFolderViewerRole_returns403AndNoRow() throws Exception {
        // userB shares the folder with userA, but VIEWER does not imply WRITE.
        folderPermissionService.grantRole(userBFolderId, userAId, FolderRole.VIEWER, userBId);

        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(publishedTemplateId, "Viewer Folder Doc " + suffix, userBFolderId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(FOLDER_DENIED));

        assertThat(documentRegistryRepository.existsByTemplateId(publishedTemplateId)).isFalse();
        assertThat(countMongoDocsForTemplate(publishedTemplateId)).isZero();
    }

    @Test
    @DisplayName("EDITOR role on foreign folder → 201 (WRITE-grade role is sufficient)")
    void create_foreignFolderEditorRole_returns201() throws Exception {
        // EDITOR implies FolderPermission.WRITE, so the folder gate must pass.
        folderPermissionService.grantRole(userBFolderId, userAId, FolderRole.EDITOR, userBId);

        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(publishedTemplateId, "Editor Folder Doc " + suffix, userBFolderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Editor Folder Doc " + suffix));

        assertThat(documentRegistryRepository.existsByTemplateId(publishedTemplateId)).isTrue();
        assertThat(countMongoDocsForTemplate(publishedTemplateId)).isEqualTo(1);
    }

    // ── missing entities ──────────────────────────────────────────────────────

    @Test
    @DisplayName("nonexistent templateId → 404 and nothing persisted")
    void create_missingTemplate_returns404() throws Exception {
        UUID missingTemplateId = UUID.randomUUID();

        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(missingTemplateId, "Missing Template Doc " + suffix, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Шаблон не найден: " + missingTemplateId));

        assertThat(documentRegistryRepository.existsByTemplateId(missingTemplateId)).isFalse();
        assertThat(countMongoDocsForTemplate(missingTemplateId)).isZero();
    }

    @Test
    @DisplayName("nonexistent folderId (accessible template) → 404 and nothing persisted")
    void create_missingFolder_returns404() throws Exception {
        // Published template → the template gate passes; the missing folder must surface as 404.
        UUID missingFolderId = UUID.randomUUID();

        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(publishedTemplateId, "Missing Folder Doc " + suffix, missingFolderId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Папка не найдена: " + missingFolderId));

        assertThat(documentRegistryRepository.existsByTemplateId(publishedTemplateId)).isFalse();
        assertThat(countMongoDocsForTemplate(publishedTemplateId)).isZero();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * The denied paths never learn the future document id, so MongoDB is inspected via the
     * (per-test unique) template id instead of {@code existsById}/{@code findById}.
     */
    private long countMongoDocsForTemplate(UUID templateId) {
        return documentRepository.findAll().stream()
                .filter(doc -> templateId.equals(doc.getTemplateId()))
                .count();
    }

    private UUID createUser(String login, String displayName) {
        User user = new User();
        user.setLogin(login);
        user.setPasswordHash("hash");
        user.setDisplayName(displayName);
        return userRepository.save(user).getId();
    }

    private static String createBody(UUID templateId, String name, UUID folderId) {
        return String.format(
                "{\"templateId\":\"%s\",\"name\":\"%s\",\"folderId\":%s}",
                templateId,
                name,
                folderId == null ? "null" : "\"" + folderId + "\"");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
