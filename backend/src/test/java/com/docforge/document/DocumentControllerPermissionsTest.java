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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer regression guard for the {@code DocumentPermission} checks on the revision /
 * questionnaire / generation endpoints (fix for BUG-048, commits {@code 2cbe399} + {@code 17b6240}).
 *
 * <p>It <strong>pins the contract</strong> — it is no longer a RED reproduction. What it locks down:</p>
 *
 * <ul>
 *   <li>{@code GET  /api/documents/{id}/revisions}                    — READ</li>
 *   <li>{@code GET  /api/documents/{id}/revisions/{revNum}}           — READ</li>
 *   <li>{@code POST /api/documents/{id}/revisions?comment=}           — WRITE</li>
 *   <li>{@code POST /api/documents/{id}/revisions/{revNum}/restore}   — WRITE</li>
 *   <li>{@code PUT  /api/documents/{id}/questionnaire/answers}        — WRITE</li>
 *   <li>{@code POST /api/documents/{id}/generate}                     — WRITE</li>
 * </ul>
 *
 * <p>Each negative case asserts both the status ({@code 403}) and the {@code detail} of the
 * {@link org.springframework.http.ProblemDetail} body, which pins the <em>style</em> of denial
 * per endpoint: READ endpoints answer {@code "Нет доступа к документу"}, WRITE endpoints answer
 * {@code "Нет прав на редактирование документа"}. The {@code *ViewerGrade*} cases additionally
 * document the intended grade — a READ-only (VIEWER-like) caller is still denied on a WRITE
 * endpoint, and the endpoint consults WRITE (never READ).</p>
 *
 * <p>Uses the same standalone MockMvc slice approach as {@link DocumentControllerDiffTest}:
 * controller instantiated with mocked collaborators, the real {@link GlobalExceptionHandler} for
 * status mapping, and {@link AuthenticationPrincipalArgumentResolver} backed by the
 * {@code SecurityContextHolder}. The real JWT / role-mapping chain is covered separately by
 * {@code DocumentControllerPermissionsIT}.</p>
 *
 * <p>Runs under the default {@code STRICT_STUBS} (no {@code @MockitoSettings}) — every stub is
 * consumed by the handler under test, which keeps the suite honest about what the controller
 * actually calls.</p>
 */
@ExtendWith(MockitoExtension.class)
class DocumentControllerPermissionsTest {

    private static final UUID DOC_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID REVISION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    private static final String LOGIN = "alice";

    /** Denial message used by READ-guarded handlers. */
    private static final String READ_DENIED = "Нет доступа к документу";
    /** Denial message used by WRITE-guarded handlers. */
    private static final String WRITE_DENIED = "Нет прав на редактирование документа";

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

        // when / then — READ-guarded: denial carries the READ-style detail
        mockMvc.perform(get("/api/documents/{id}/revisions", DOC_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(READ_DENIED));

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

        // when / then — READ-guarded: denial carries the READ-style detail
        mockMvc.perform(get("/api/documents/{id}/revisions/{rev}", DOC_ID, 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(READ_DENIED));

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

        // when / then — WRITE-guarded: denial carries the WRITE-style detail
        mockMvc.perform(post("/api/documents/{id}/revisions", DOC_ID).param("comment", "manual"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(WRITE_DENIED));

        verifyNoInteractions(documentService);
        verify(documentPermissionService).hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE);
    }

    @Test
    @DisplayName("POST .../revisions denies a READ-only (VIEWER-like) caller with 403")
    void saveRevision_withViewerGrade_returns403() throws Exception {
        // given — effective grade is READ-only: reading allowed, writing denied
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        givenReadOnlyGrade();

        // when / then
        mockMvc.perform(post("/api/documents/{id}/revisions", DOC_ID).param("comment", "viewer"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(WRITE_DENIED));

        // then — the endpoint is graded WRITE; READ alone never unlocks it
        verify(documentPermissionService).hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE);
        verify(documentPermissionService, never()).hasPermission(DOC_ID, USER_ID, DocumentPermission.READ);
        verifyNoInteractions(documentService);
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

        // when / then — WRITE-guarded: denial carries the WRITE-style detail
        mockMvc.perform(post("/api/documents/{id}/revisions/{rev}/restore", DOC_ID, 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(WRITE_DENIED));

        verifyNoInteractions(documentService);
        verify(documentPermissionService).hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE);
    }

    @Test
    @DisplayName("POST .../revisions/{revNum}/restore denies a READ-only (VIEWER-like) caller with 403")
    void restoreRevision_withViewerGrade_returns403() throws Exception {
        // given — effective grade is READ-only: reading allowed, writing denied
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        givenReadOnlyGrade();

        // when / then
        mockMvc.perform(post("/api/documents/{id}/revisions/{rev}/restore", DOC_ID, 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(WRITE_DENIED));

        // then — the endpoint is graded WRITE; READ alone never unlocks it
        verify(documentPermissionService).hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE);
        verify(documentPermissionService, never()).hasPermission(DOC_ID, USER_ID, DocumentPermission.READ);
        verifyNoInteractions(documentService);
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

        // when / then — WRITE-guarded: denial carries the WRITE-style detail
        mockMvc.perform(put("/api/documents/{id}/questionnaire/answers", DOC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"q1\":\"a\"}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(WRITE_DENIED));

        verifyNoInteractions(documentService);
        verify(documentPermissionService).hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE);
    }

    @Test
    @DisplayName("PUT .../questionnaire/answers denies a READ-only (VIEWER-like) caller with 403")
    void saveAnswers_withViewerGrade_returns403() throws Exception {
        // given — effective grade is READ-only: reading allowed, writing denied
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        givenReadOnlyGrade();

        // when / then
        mockMvc.perform(put("/api/documents/{id}/questionnaire/answers", DOC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"q1\":\"a\"}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(WRITE_DENIED));

        // then — the endpoint is graded WRITE; READ alone never unlocks it
        verify(documentPermissionService).hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE);
        verify(documentPermissionService, never()).hasPermission(DOC_ID, USER_ID, DocumentPermission.READ);
        verifyNoInteractions(documentService);
    }

    // ── generation ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST .../generate with WRITE permission returns 200")
    void generate_withWritePermission_returns200() throws Exception {
        // given — caller may write (generation mutates the document body)
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE))
                .thenReturn(true);
        when(documentService.generate(DOC_ID))
                .thenReturn(new GenerateResponse(DOC_ID, Map.of("type", "doc")));

        // when / then
        mockMvc.perform(post("/api/documents/{id}/generate", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(DOC_ID.toString()));
    }

    @Test
    @DisplayName("POST .../generate without WRITE permission returns 403 and never generates")
    void generate_withoutWritePermission_returns403() throws Exception {
        // given — authenticated caller has no WRITE permission
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE))
                .thenReturn(false);

        // when / then — WRITE-guarded: denial carries the WRITE-style detail
        mockMvc.perform(post("/api/documents/{id}/generate", DOC_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(WRITE_DENIED));

        verifyNoInteractions(documentService);
        verify(documentPermissionService).hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE);
    }

    @Test
    @DisplayName("POST .../generate denies a READ-only (VIEWER-like) caller with 403")
    void generate_withViewerGrade_returns403() throws Exception {
        // given — effective grade is READ-only: reading allowed, writing denied
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        givenReadOnlyGrade();

        // when / then
        mockMvc.perform(post("/api/documents/{id}/generate", DOC_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(WRITE_DENIED));

        // then — the endpoint is graded WRITE; READ alone never unlocks it
        verify(documentPermissionService).hasPermission(DOC_ID, USER_ID, DocumentPermission.WRITE);
        verify(documentPermissionService, never()).hasPermission(DOC_ID, USER_ID, DocumentPermission.READ);
        verifyNoInteractions(documentService);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * Stubs a READ-only ("VIEWER-like") effective grade: read/VIEW probes succeed, WRITE fails.
     *
     * <p>Modelled as a single {@code thenAnswer} so the stub is actually consumed by the WRITE
     * probe the handler under test performs. A bare {@code hasPermission(READ)=true} stub — which
     * no WRITE-guarded handler ever queries — would trip {@code UnnecessaryStubbingException}
     * under the default strict stubbing.</p>
     */
    private void givenReadOnlyGrade() {
        when(documentPermissionService.hasPermission(eq(DOC_ID), eq(USER_ID), any(DocumentPermission.class)))
                .thenAnswer(invocation -> {
                    DocumentPermission requested = invocation.getArgument(2);
                    return requested == DocumentPermission.READ || requested == DocumentPermission.VIEW;
                });
    }

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
