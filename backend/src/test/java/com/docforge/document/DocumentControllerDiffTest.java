package com.docforge.document;

import com.docforge.common.exception.GlobalExceptionHandler;
import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.document.dto.DiffChunk;
import com.docforge.document.dto.DiffChunk.DiffChunkType;
import com.docforge.document.dto.RevisionDiffResponse;
import com.docforge.document.generator.ParameterSubstitutor;
import com.docforge.document.generator.TipTapTreeWalker;
import com.docforge.document.model.DocumentPermission;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer contract tests for {@code GET /api/documents/{id}/revisions/{revNum}/diff}.
 *
 * <p>Standalone MockMvc slice: the controller is instantiated with mocked collaborators and the
 * real {@link GlobalExceptionHandler} so status-code mapping (404 / 403) is verified end-to-end
 * without spinning up Testcontainers.</p>
 */
@ExtendWith(MockitoExtension.class)
class DocumentControllerDiffTest {

    private static final UUID DOC_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
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

    // ── happy path ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET .../diff returns 200 with chunks when caller has READ permission")
    void getRevisionDiff_withReadPermission_returns200WithChunks() throws Exception {
        // given — caller may read and the service returns an EQUAL + INSERT diff
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.READ))
                .thenReturn(true);
        when(documentService.computeRevisionDiff(DOC_ID, 2, 1)).thenReturn(
                new RevisionDiffResponse(
                        UUID.randomUUID(), 2, 1, false,
                        List.of(
                                new DiffChunk(DiffChunkType.EQUAL, "same\n"),
                                new DiffChunk(DiffChunkType.INSERT, "new\n"))));

        // when / then
        mockMvc.perform(get("/api/documents/{id}/revisions/{rev}/diff", DOC_ID, 2)
                        .param("compareTo", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisionNumber").value(2))
                .andExpect(jsonPath("$.compareToRevisionNumber").value(1))
                .andExpect(jsonPath("$.compareToCurrent").value(false))
                .andExpect(jsonPath("$.diff[0].type").value("EQUAL"))
                .andExpect(jsonPath("$.diff[1].type").value("INSERT"))
                .andExpect(jsonPath("$.diff[1].text").value("new\n"));
    }

    @Test
    @DisplayName("GET .../diff without compareTo returns 200 diffing against current body")
    void getRevisionDiff_withoutCompareTo_returns200() throws Exception {
        // given — no compareTo → null → service diffs against current state
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.READ))
                .thenReturn(true);
        when(documentService.computeRevisionDiff(DOC_ID, 3, null)).thenReturn(
                new RevisionDiffResponse(
                        UUID.randomUUID(), 3, null, true,
                        List.of(new DiffChunk(DiffChunkType.EQUAL, "unchanged\n"))));

        // when / then
        mockMvc.perform(get("/api/documents/{id}/revisions/{rev}/diff", DOC_ID, 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compareToCurrent").value(true))
                .andExpect(jsonPath("$.compareToRevisionNumber").doesNotExist());
    }

    // ── error mapping ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET .../diff for unknown revision maps ResourceNotFoundException to 404")
    void getRevisionDiff_unknownRevision_returns404() throws Exception {
        // given — permission granted but the revision does not exist
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.READ))
                .thenReturn(true);
        when(documentService.computeRevisionDiff(DOC_ID, 99, null))
                .thenThrow(new ResourceNotFoundException("Редакция не найдена: " + 99));

        // when / then
        mockMvc.perform(get("/api/documents/{id}/revisions/{rev}/diff", DOC_ID, 99))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET .../diff without READ permission returns 403 and never calls the service")
    void getRevisionDiff_withoutReadPermission_returns403() throws Exception {
        // given — caller is authenticated but has no READ permission
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.READ))
                .thenReturn(false);

        // when / then
        mockMvc.perform(get("/api/documents/{id}/revisions/{rev}/diff", DOC_ID, 1))
                .andExpect(status().isForbidden());

        // then — the diff is never computed
        verifyNoInteractions(documentService);
    }
}
