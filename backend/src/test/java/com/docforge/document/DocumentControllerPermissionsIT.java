package com.docforge.document;

import com.docforge.AbstractIT;
import com.docforge.document.model.DocumentRole;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the {@code DocumentPermission} checks on the revision /
 * questionnaire / generation endpoints (fix for BUG-048).
 *
 * <p>Unlike {@link DocumentControllerPermissionsTest} (a standalone MockMvc slice with a mocked
 * permission service), this IT exercises the <strong>real security chain</strong> end to end:</p>
 * <ol>
 *   <li>a freshly minted JWT is sent in {@code Authorization: Bearer …};</li>
 *   <li>{@code JwtAuthFilter} validates it and resolves the principal via
 *       {@code UserDetailsServiceImpl} (real {@code users} table);</li>
 *   <li>the controller receives the principal through {@code @AuthenticationPrincipal} and resolves
 *       the user id via {@code DocumentOwnerResolver};</li>
 *   <li>the <strong>real</strong> {@code DocumentPermissionServiceImpl} maps the stored
 *       {@code DocumentMember} role (OWNER / EDITOR / VIEWER / none) to a permission decision.</li>
 * </ol>
 *
 * <p>This pins the intended grade: VIEWER may read but never write; EDITOR/OWNER may write.
 * A non-participant of an existing document is denied (403) because
 * {@code getEffectiveRole} returns {@code null} rather than throwing; a <em>non-existent</em>
 * document yields 404 because {@code getEffectiveRole} throws {@code ResourceNotFoundException}
 * before the controller's own forbidden path. Both orders are asserted deliberately.</p>
 */
@AutoConfigureMockMvc
class DocumentControllerPermissionsIT extends AbstractIT {

    private static final String READ_DENIED = "Нет доступа к документу";
    private static final String WRITE_DENIED = "Нет прав на редактирование документа";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private DocumentService documentService;
    @Autowired private DocumentPermissionService documentPermissionService;
    @Autowired private TemplateService templateService;

    private UUID documentId;
    private String ownerToken;
    private String editorToken;
    private String viewerToken;
    private String strangerToken;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        String ownerLogin = "perm-owner-" + suffix;
        String editorLogin = "perm-editor-" + suffix;
        String viewerLogin = "perm-viewer-" + suffix;
        String strangerLogin = "perm-stranger-" + suffix;

        UUID ownerId = createUser(ownerLogin, "Perm Owner");
        UUID editorId = createUser(editorLogin, "Perm Editor");
        UUID viewerId = createUser(viewerLogin, "Perm Viewer");
        createUser(strangerLogin, "Perm Stranger");

        ownerToken = jwtTokenProvider.generateToken(ownerLogin);
        editorToken = jwtTokenProvider.generateToken(editorLogin);
        viewerToken = jwtTokenProvider.generateToken(viewerLogin);
        strangerToken = jwtTokenProvider.generateToken(strangerLogin);

        // The template must belong to the future document owner: BUG-052's guard in
        // DocumentServiceImpl.create only allows published templates or ones owned by the creator.
        TemplateResponse template = templateService.create(
                ownerId,
                new CreateTemplateRequest("PermissionsIT Template " + suffix, null, null, null));

        documentId = documentService.create(
                template.id(), "PermissionsIT Doc " + suffix, ownerId, null, null).id();

        documentPermissionService.grantRole(documentId, editorId, DocumentRole.EDITOR, ownerId);
        documentPermissionService.grantRole(documentId, viewerId, DocumentRole.VIEWER, ownerId);
    }

    // ── VIEWER: read yes, write no ─────────────────────────────────────────────

    @Test
    @DisplayName("VIEWER participant gets 403 on PUT /questionnaire/answers (WRITE-guarded)")
    void viewer_saveAnswers_returns403() throws Exception {
        mockMvc.perform(put("/api/documents/{id}/questionnaire/answers", documentId)
                        .header("Authorization", bearer(viewerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"q1\":\"a\"}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(WRITE_DENIED));
    }

    @Test
    @DisplayName("VIEWER participant gets 403 on POST /generate (WRITE-guarded)")
    void viewer_generate_returns403() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/generate", documentId)
                        .header("Authorization", bearer(viewerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(WRITE_DENIED));
    }

    @Test
    @DisplayName("VIEWER participant may still read: GET /revisions returns 200")
    void viewer_listRevisions_returns200() throws Exception {
        mockMvc.perform(get("/api/documents/{id}/revisions", documentId)
                        .header("Authorization", bearer(viewerToken)))
                .andExpect(status().isOk());
    }

    // ── OWNER / EDITOR: write allowed ──────────────────────────────────────────

    @Test
    @DisplayName("OWNER gets 200 on PUT /questionnaire/answers")
    void owner_saveAnswers_returns200() throws Exception {
        mockMvc.perform(put("/api/documents/{id}/questionnaire/answers", documentId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"q1\":\"owner-answer\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.q1").value("owner-answer"));
    }

    @Test
    @DisplayName("EDITOR gets 200 on POST /generate")
    void editor_generate_returns200() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/generate", documentId)
                        .header("Authorization", bearer(editorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(documentId.toString()));
    }

    @Test
    @DisplayName("EDITOR gets 201 on POST /revisions")
    void editor_saveRevision_returns201() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/revisions", documentId)
                        .header("Authorization", bearer(editorToken))
                        .param("comment", "editor snapshot"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(documentId.toString()));
    }

    // ── non-participant / unknown document: deliberate order-of-operations ─────

    @Test
    @DisplayName("non-participant (no role on an existing document) gets 403, not 404")
    void nonParticipant_saveAnswers_returns403() throws Exception {
        // getEffectiveRole returns null (no exception) for a user with no role → hasPermission=false → 403
        mockMvc.perform(put("/api/documents/{id}/questionnaire/answers", documentId)
                        .header("Authorization", bearer(strangerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"q1\":\"a\"}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(WRITE_DENIED));
    }

    @Test
    @DisplayName("unknown document id gets 404 (getEffectiveRole throws before the forbidden path)")
    void unknownDocument_saveAnswers_returns404() throws Exception {
        UUID unknown = UUID.randomUUID();
        mockMvc.perform(put("/api/documents/{id}/questionnaire/answers", unknown)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"q1\":\"a\"}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("request without a token is rejected by the security chain with 403")
    void noToken_saveAnswers_returns403() throws Exception {
        // SecurityConfig declares no custom AuthenticationEntryPoint, so Spring Security's default
        // Http403ForbiddenEntryPoint rejects the anonymous request with 403 (not 401) before the
        // controller — pinning that behaviour here so a future change to the entry point is visible.
        mockMvc.perform(put("/api/documents/{id}/questionnaire/answers", documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"q1\":\"a\"}}"))
                .andExpect(status().isForbidden());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

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
}
