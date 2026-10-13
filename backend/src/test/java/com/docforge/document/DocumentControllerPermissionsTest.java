package com.docforge.document;

import com.docforge.common.exception.GlobalExceptionHandler;
import com.docforge.document.dto.DocumentRevisionDTO;
import com.docforge.document.dto.GenerateResponse;
import com.docforge.document.dto.SaveAnswersResponse;
import com.docforge.document.generator.ParameterSubstitutor;
import com.docforge.document.generator.TipTapTreeWalker;
import com.docforge.document.model.DocumentPermission;
import com.docforge.document.model.DocumentRevisionBody;
import com.docforge.export.DocxExportService;
import com.docforge.export.NodeResolver;
import com.docforge.export.PdfExportService;
import com.docforge.template.TemplateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer contract tests for the {@code DocumentPermission} checks that BUG-048 requires on the
 * revision / questionnaire / generation endpoints:
 *
 * <ul>
 *   <li>{@code GET  /api/documents/{id}/revisions}                    — READ</li>
 *   <li>{@code GET  /api/documents/{id}/revisions/{revNum}}           — READ</li>
 *   <li>{@code POST /api/documents/{id}/revisions?comment=}           — WRITE</li>
 *   <li>{@code POST /api/documents/{id}/revisions/{revNum}/restore}   — WRITE</li>
 *   <li>{@code PUT  /api/documents/{id}/questionnaire/answers}        — WRITE</li>
 *   <li>{@code POST /api/documents/{id}/generate}                     — READ</li>
 * </ul>
 *
 * <p>These tests are the automated RED reproduction for BUG-048: the production controller currently
 * has <strong>no</strong> {@code documentPermissionService.hasPermission(...)} call (and mostly no
 * {@code @AuthenticationPrincipal}) on these handlers, so the "no permission → 403" cases fail with
 * {@code expected 403 but was 200/201}. They only exercise the public HTTP contract, therefore they
 * are a genuine behavioural RED — not a compile error.</p>
 *
 * <p>Uses the same standalone MockMvc slice approach as {@link DocumentControllerDiffTest}:
 * controller instantiated with mocked collaborators, the real {@link GlobalExceptionHandler} for
 * status mapping, and {@link AuthenticationPrincipalArgumentResolver} backed by the
 * {@code SecurityContextHolder}.</p>
 *
 * <p>Strictness is {@code LENIENT} on purpose: the {@code ownerResolver}/{@code hasPermission} stubs
 * represent the <em>intended</em> contract that production does not call yet. With strict stubs
 * Mockito would raise {@code UnnecessaryStubbingException} on the (currently passing) positive cases
 * and mask the real 403 RED on the negative cases.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentControllerPermissionsTest {

    private static final UUID DOC_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID REVISION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    private static final String LOGIN = "alice";

    @Mock private DocumentService documentService;
    @Mock private DocumentPermissionService documentPermissionService;
    @Mock private FolderPermissionService folderPermissionService;
    @Mock private DocumentOwnerResolver ownerResolver;
    @Mock private DocxExportService docxExportService;
    @Mock private PdfExportService pdfExportService;
    @Mock private TemplateService templateService;
    @Mock private TipTapTreeWalker treeWalker;
    @Mock private ParameterSubstitutor substitutor;
    @Mock private NodeResolver nodeResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DocumentController controller = new DocumentController(
                documentService,
                documentPermissionService,
                folderPermissionService,
                ownerResolver,
                docxExportService,
                pdfExportService,
                templateService,
                treeWalker,
                substitutor,
                nodeResolver);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        UserDetails principal = new User(LOGIN, "", List.of());
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── revisions: list ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET .../revisions with READ permission returns 200")
    void listRevisions_withReadPermission_returns200() throws Exception {
        // given — caller may read
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.READ))
                .thenReturn(true);
        when(documentService.listRevisions(DOC_ID)).thenReturn(List.of(revision(1), revision(2)));

        // when / then
        mockMvc.perform(get("/api/documents/{id}/revisions", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].editionNumber").value(1))
                .andExpect(jsonPath("$[1].editionNumber").value(2));
    }

    @Test
    @DisplayName("GET .../revisions without READ permission returns 403 and never reads the history")
    void listRevisions_withoutReadPermission_returns403() throws Exception {
        // given — authenticated caller has no READ permission
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.READ))
                .thenReturn(false);

        // when / then — BUG-048: currently 200, because the handler never checks the permission
        mockMvc.perform(get("/api/documents/{id}/revisions", DOC_ID))
                .andExpect(status().isForbidden());

        verifyNoInteractions(documentService);
        verify(documentPermissionService).hasPermission(DOC_ID, USER_ID, DocumentPermission.READ);
    }

    // ── revisions: single body ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET .../revisions/{revNum} with READ permission returns 200")
    void getRevision_withReadPermission_returns200() throws Exception {
        // given — caller may read
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.READ))
                .thenReturn(true);
        when(documentService.getRevisionBody(DOC_ID, 1)).thenReturn(revisionBody(1));

        // when / then
        mockMvc.perform(get("/api/documents/{id}/revisions/{rev}", DOC_ID, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisionNumber").value(1))
                .andExpect(jsonPath("$.documentId").value(DOC_ID.toString()));
    }

    @Test
    @DisplayName("GET .../revisions/{revNum} without READ permission returns 403 and never loads the body")
    void getRevision_withoutReadPermission_returns403() throws Exception {
        // given — authenticated caller has no READ permission
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.READ))
                .thenReturn(false);

        // when / then — BUG-048: currently 200, because the handler never checks the permission
        mockMvc.perform(get("/api/documents/{id}/revisions/{rev}", DOC_ID, 1))
                .andExpect(status().isForbidden());

        verifyNoInteractions(documentService);
        verify(documentPermissionService).hasPermission(DOC_ID, USER_ID, DocumentPermission.READ);
    }

    // ── revisions: save ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST .../revisions with WRITE permission returns 201")
    void saveRevision_withWritePermission_returns201() throws Exception {
        // given — caller may write
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE))
                .thenReturn(true);
        when(documentService.saveRevision(DOC_ID, "manual")).thenReturn(revision(3));

        // when / then
        mockMvc.perform(post("/api/documents/{id}/revisions", DOC_ID).param("comment", "manual"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.editionNumber").value(3));
    }

    @Test
    @DisplayName("POST .../revisions without WRITE permission returns 403 and never snapshots")
    void saveRevision_withoutWritePermission_returns403() throws Exception {
        // given — authenticated caller has no WRITE permission
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE))
                .thenReturn(false);

        // when / then — BUG-048: currently 201, because the handler never checks the permission
        mockMvc.perform(post("/api/documents/{id}/revisions", DOC_ID).param("comment", "manual"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(documentService);
        verify(documentPermissionService).hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE);
    }

    // ── revisions: restore ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST .../revisions/{revNum}/restore with WRITE permission returns 200")
    void restoreRevision_withWritePermission_returns200() throws Exception {
        // given — caller may write
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE))
                .thenReturn(true);
        when(documentService.restoreRevision(DOC_ID, 1)).thenReturn(revision(4));

        // when / then
        mockMvc.perform(post("/api/documents/{id}/revisions/{rev}/restore", DOC_ID, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editionNumber").value(4));
    }

    @Test
    @DisplayName("POST .../revisions/{revNum}/restore without WRITE permission returns 403 and never restores")
    void restoreRevision_withoutWritePermission_returns403() throws Exception {
        // given — authenticated caller has no WRITE permission
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE))
                .thenReturn(false);

        // when / then — BUG-048: currently 200, because the handler never checks the permission
        mockMvc.perform(post("/api/documents/{id}/revisions/{rev}/restore", DOC_ID, 1))
                .andExpect(status().isForbidden());

        verifyNoInteractions(documentService);
        verify(documentPermissionService).hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE);
    }

    // ── questionnaire answers ──────────────────────────────────────────────────

    @Test
    @DisplayName("PUT .../questionnaire/answers with WRITE permission returns 200")
    void saveAnswers_withWritePermission_returns200() throws Exception {
        // given — caller may write
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE))
                .thenReturn(true);
        when(documentService.saveAnswers(DOC_ID, Map.of("q1", "a")))
                .thenReturn(new SaveAnswersResponse(Map.of("q1", "a"), null));

        // when / then
        mockMvc.perform(put("/api/documents/{id}/questionnaire/answers", DOC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"q1\":\"a\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.q1").value("a"));
    }

    @Test
    @DisplayName("PUT .../questionnaire/answers without WRITE permission returns 403 and never persists")
    void saveAnswers_withoutWritePermission_returns403() throws Exception {
        // given — authenticated caller has no WRITE permission
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE))
                .thenReturn(false);

        // when / then — BUG-048: currently 200, because the handler never checks the permission
        mockMvc.perform(put("/api/documents/{id}/questionnaire/answers", DOC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"q1\":\"a\"}}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(documentService);
        verify(documentPermissionService).hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE);
    }

    // ── generation ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST .../generate with READ permission returns 200")
    void generate_withReadPermission_returns200() throws Exception {
        // given — caller may read
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.READ))
                .thenReturn(true);
        when(documentService.generate(DOC_ID))
                .thenReturn(new GenerateResponse(DOC_ID, Map.of("type", "doc")));

        // when / then
        mockMvc.perform(post("/api/documents/{id}/generate", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(DOC_ID.toString()));
    }

    @Test
    @DisplayName("POST .../generate without READ permission returns 403 and never generates")
    void generate_withoutReadPermission_returns403() throws Exception {
        // given — authenticated caller has no READ permission
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.READ))
                .thenReturn(false);

        // when / then — BUG-048: currently 200, because the handler never checks the permission
        mockMvc.perform(post("/api/documents/{id}/generate", DOC_ID))
                .andExpect(status().isForbidden());

        verifyNoInteractions(documentService);
        verify(documentPermissionService).hasPermission(DOC_ID, USER_ID, DocumentPermission.READ);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static DocumentRevisionDTO revision(int editionNumber) {
        return new DocumentRevisionDTO(
                REVISION_ID,
                DOC_ID,
                editionNumber,
                USER_ID,
                "Alice",
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                "rev " + editionNumber);
    }

    private static DocumentRevisionBody revisionBody(int revisionNumber) {
        return DocumentRevisionBody.builder()
                .id(REVISION_ID)
                .documentId(DOC_ID)
                .revisionNumber(revisionNumber)
                .body(Map.of("type", "doc"))
                .answers(Map.of())
                .savedBy(USER_ID)
                .savedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .comment("rev " + revisionNumber)
                .build();
    }
}
