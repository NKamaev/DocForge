package com.docforge.document;

import com.docforge.AbstractIT;
import com.docforge.document.model.DocumentMember;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the role guards on the legacy participants endpoints
 * (fix for BUG-051).
 *
 * <p>Unlike {@code DocumentControllerParticipantsTest} (a standalone MockMvc slice), this IT
 * exercises the <strong>real security chain</strong> end to end: a real JWT →
 * {@code JwtAuthFilter} → {@code UserDetailsServiceImpl} → {@code DocumentOwnerResolver} →
 * the real {@code DocumentPermissionServiceImpl} backed by the {@code document_members} table.</p>
 *
 * <p>Pinned behaviour of {@code POST /api/documents/{id}/participants}:</p>
 * <ol>
 *   <li>the caller needs {@code DocumentPermission.SHARE} — MANAGER and OWNER pass, EDITOR is
 *       denied with 403 «Нет прав на управление участниками»;</li>
 *   <li>an unknown role string yields 400 «Неизвестная роль: X; допустимо: ...» before anything is written;</li>
 *   <li>assigning {@code OWNER} is only allowed when the caller's effective role is OWNER,
 *       otherwise 422 «Нельзя назначить роль OWNER» and the target keeps no member row.</li>
 * </ol>
 *
 * <p>Pinned behaviour of {@code DELETE /api/documents/{id}/participants/{userId}}: the same
 * SHARE gate, plus a 422 «Нельзя удалить OWNER из участников» when the target's effective role
 * is OWNER (the owner membership must survive the attempt).</p>
 *
 * <p>The canonical {@code POST /api/documents/{id}/members} controller throws the same 422; after
 * the {@code GlobalExceptionHandler} fix it must surface as <strong>422, not 500</strong>.</p>
 */
@AutoConfigureMockMvc
class DocumentParticipantsPermissionIT extends AbstractIT {

    private static final String SHARE_DENIED = "Нет прав на управление участниками";
    private static final String OWNER_GRANT_DENIED = "Нельзя назначить роль OWNER";
    private static final String OWNER_DELETE_DENIED = "Нельзя удалить OWNER из участников";
    private static final String OWNER_PUT_DENIED = "Нельзя изменить роль OWNER";
    private static final String OWNER_CHANGE_DENIED = "Нельзя изменить роль владельца документа";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private DocumentService documentService;
    @Autowired private DocumentPermissionService documentPermissionService;
    @Autowired private DocumentMemberRepository documentMemberRepository;
    @Autowired private TemplateService templateService;

    private UUID documentId;
    private UUID ownerId;
    private UUID managerId;
    private UUID editorId;
    private UUID targetId;

    private String ownerToken;
    private String managerToken;
    private String editorToken;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        String ownerLogin = "part-owner-" + suffix;
        String managerLogin = "part-manager-" + suffix;
        String editorLogin = "part-editor-" + suffix;

        ownerId = createUser(ownerLogin, "Part Owner");
        managerId = createUser(managerLogin, "Part Manager");
        editorId = createUser(editorLogin, "Part Editor");
        targetId = createUser("part-target-" + suffix, "Part Target");

        ownerToken = jwtTokenProvider.generateToken(ownerLogin);
        managerToken = jwtTokenProvider.generateToken(managerLogin);
        editorToken = jwtTokenProvider.generateToken(editorLogin);

        // The template is owned by the (future) document owner, so the BUG-052 guard in
        // DocumentServiceImpl.create lets it through regardless of publication state.
        TemplateResponse template = templateService.create(
                ownerId, new CreateTemplateRequest("ParticipantsPermIT Template " + suffix, null, null, null));

        documentId = documentService.create(
                template.id(), "ParticipantsPermIT Doc " + suffix, ownerId, null, null).id();

        documentPermissionService.grantRole(documentId, managerId, DocumentRole.MANAGER, ownerId);
        documentPermissionService.grantRole(documentId, editorId, DocumentRole.EDITOR, ownerId);
    }

    // ── POST /participants: OWNER escalation guard ─────────────────────────────

    @Test
    @DisplayName("MANAGER grants OWNER via legacy /participants → 422 and no member row created")
    void manager_grantOwner_returns422AndNoRow() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/participants", documentId)
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(targetId, "OWNER")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(OWNER_GRANT_DENIED));

        assertThat(findMember(targetId)).isEmpty();
        assertThat(documentPermissionService.getEffectiveRole(documentId, targetId)).isNull();
    }

    @Test
    @DisplayName("owner grants OWNER via legacy /participants → 201")
    void owner_grantOwner_returns201() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/participants", documentId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(targetId, "OWNER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(targetId.toString()))
                .andExpect(jsonPath("$.role").value("OWNER"));

        assertThat(documentPermissionService.getEffectiveRole(documentId, targetId))
                .isEqualTo(DocumentRole.OWNER);
    }

    @Test
    @DisplayName("MANAGER demotes an already-granted OWNER to EDITOR via legacy /participants → 422 and role stays OWNER")
    void manager_demoteGrantedOwner_returns422AndRoleStaysOwner() throws Exception {
        // given — the owner already granted C the OWNER role
        documentPermissionService.grantRole(documentId, targetId, DocumentRole.OWNER, ownerId);

        // when — the manager tries to demote C via the legacy endpoint
        mockMvc.perform(post("/api/documents/{id}/participants", documentId)
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(targetId, "EDITOR")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(OWNER_CHANGE_DENIED));

        // then — the OWNER membership is untouched
        assertThat(documentPermissionService.getEffectiveRole(documentId, targetId))
                .isEqualTo(DocumentRole.OWNER);
        assertThat(findMember(targetId)).get()
                .extracting(DocumentMember::getRole).isEqualTo("OWNER");
    }

    @Test
    @DisplayName("MANAGER targets the registry owner via legacy /participants → 422 and no member row created")
    void manager_grantToRegistryOwner_returns422AndNoRow() throws Exception {
        // when — the document's registry owner is already effectively OWNER
        mockMvc.perform(post("/api/documents/{id}/participants", documentId)
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(ownerId, "EDITOR")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(OWNER_CHANGE_DENIED));

        // then — no invisible duplicate member row is persisted for the registry owner
        assertThat(findMember(ownerId)).isEmpty();
        assertThat(documentPermissionService.getEffectiveRole(documentId, ownerId))
                .isEqualTo(DocumentRole.OWNER);
    }

    @Test
    @DisplayName("legacy /participants persists the acting user as granted_by")
    void owner_grantEditor_persistsGrantedBy() throws Exception {
        // when — the document owner grants EDITOR through the legacy endpoint
        mockMvc.perform(post("/api/documents/{id}/participants", documentId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(targetId, "EDITOR")))
                .andExpect(status().isCreated());

        // then — granted_by carries the actor id, not null
        assertThat(findMember(targetId)).get()
                .extracting(DocumentMember::getGrantedBy).isEqualTo(ownerId);
    }

    // ── POST /participants: role parsing ──────────────────────────────────────

    @Test
    @DisplayName("MANAGER grants EDITOR via legacy /participants → 201 and effective role EDITOR")
    void manager_grantEditor_returns201() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/participants", documentId)
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(targetId, "EDITOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(targetId.toString()))
                .andExpect(jsonPath("$.role").value("EDITOR"));

        assertThat(documentPermissionService.getEffectiveRole(documentId, targetId))
                .isEqualTo(DocumentRole.EDITOR);
    }

    @Test
    @DisplayName("MANAGER posts an unknown role → 400 «Неизвестная роль» and no member row created")
    void manager_grantUnknownRole_returns400() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/participants", documentId)
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(targetId, "SUPERUSER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(
                        "Неизвестная роль: SUPERUSER; допустимо: VIEWER, EDITOR, MANAGER, OWNER"));

        assertThat(findMember(targetId)).isEmpty();
    }

    // ── DELETE /participants/{userId}: OWNER guard ────────────────────────────

    @Test
    @DisplayName("MANAGER deletes the document OWNER via legacy /participants → 422 and membership preserved")
    void manager_deleteOwner_returns422AndOwnerPreserved() throws Exception {
        mockMvc.perform(delete("/api/documents/{id}/participants/{userId}", documentId, ownerId)
                        .header("Authorization", bearer(managerToken)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(OWNER_DELETE_DENIED));

        assertThat(documentPermissionService.getEffectiveRole(documentId, ownerId))
                .isEqualTo(DocumentRole.OWNER);
    }

    // ── SHARE gate: EDITOR is denied on both verbs ────────────────────────────

    @Test
    @DisplayName("EDITOR gets 403 on POST /participants (SHARE-guarded)")
    void editor_grant_returns403() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/participants", documentId)
                        .header("Authorization", bearer(editorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(targetId, "EDITOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(SHARE_DENIED));

        assertThat(findMember(targetId)).isEmpty();
    }

    @Test
    @DisplayName("EDITOR gets 403 on DELETE /participants/{userId} (SHARE-guarded)")
    void editor_delete_returns403() throws Exception {
        mockMvc.perform(delete("/api/documents/{id}/participants/{userId}", documentId, managerId)
                        .header("Authorization", bearer(editorToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(SHARE_DENIED));

        assertThat(documentPermissionService.getEffectiveRole(documentId, managerId))
                .isEqualTo(DocumentRole.MANAGER);
    }

    // ── canonical /members: same 422, no longer leaked as 500 ─────────────────

    @Test
    @DisplayName("MANAGER demotes an already-granted OWNER via canonical PUT /members → 422 and role stays OWNER")
    void manager_demoteGrantedOwnerViaPut_returns422AndRoleStaysOwner() throws Exception {
        // given — the owner already granted C the OWNER role
        documentPermissionService.grantRole(documentId, targetId, DocumentRole.OWNER, ownerId);

        // when — the manager demotes C through the canonical endpoint
        mockMvc.perform(put("/api/documents/{id}/members/{userId}", documentId, targetId)
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"EDITOR\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(OWNER_PUT_DENIED));

        // then — the OWNER membership survives
        assertThat(documentPermissionService.getEffectiveRole(documentId, targetId))
                .isEqualTo(DocumentRole.OWNER);
    }

    @Test
    @DisplayName("MANAGER grants OWNER via canonical /members → 422 (not 500) after the handler fix")
    void manager_grantOwnerViaMembers_returns422Not500() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/members", documentId)
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(targetId, "OWNER")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(OWNER_GRANT_DENIED));

        assertThat(findMember(targetId)).isEmpty();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Optional<DocumentMember> findMember(UUID userId) {
        return documentMemberRepository.findByDocumentIdAndUserId(documentId, userId);
    }

    private UUID createUser(String login, String displayName) {
        User user = new User();
        user.setLogin(login);
        user.setPasswordHash("hash");
        user.setDisplayName(displayName);
        return userRepository.save(user).getId();
    }

    private static String grantBody(UUID userId, String role) {
        return String.format("{\"userId\":\"%s\",\"role\":\"%s\"}", userId, role);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
