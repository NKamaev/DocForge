package com.docforge.document;

import com.docforge.common.exception.GlobalExceptionHandler;
import com.docforge.common.format.FioFormatterService;
import com.docforge.common.format.NumberToWordsService;
import com.docforge.document.dto.DocumentResponse;
import com.docforge.document.generator.ParameterSubstitutor;
import com.docforge.document.generator.TipTapTreeWalker;
import com.docforge.document.model.DocumentPermission;
import com.docforge.export.DocxExportService;
import com.docforge.export.DocxExportServiceImpl;
import com.docforge.export.NodeResolver;
import com.docforge.export.PdfExportService;
import com.docforge.export.PdfExportServiceImpl;
import com.docforge.template.TemplateService;
import com.docforge.template.model.Parameter;
import com.docforge.template.model.ParameterType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression guard for BUG-021 — {@link NullPointerException} in
 * {@code DocumentController.applyAnswers()} when a TipTap node has no {@code "type"} field.
 *
 * <p>A document body may legitimately contain a node without a {@code "type"} key (or with an
 * explicit {@code "type": null}) — e.g. after a Yjs/CRDT merge produced a malformed fragment.
 * The original code did {@code switch ((String) node.get("type"))}, which threw an NPE as soon
 * as the {@code switch} tried to unbox {@code null} (the classic {@code switch}-on-String null
 * NPE) and turned both {@code GET .../export/docx} and {@code GET .../export/pdf} into 500s.
 * The fix switches on {@code String.valueOf(node.get("type"))}, so a missing type yields the
 * literal {@code "null"} and falls into {@code default} — the node is kept verbatim.</p>
 *
 * <h2>Why a real walker (and not the mocked one used elsewhere)</h2>
 *
 * <p>{@link DocumentControllerPermissionsTest} mocks {@link TipTapTreeWalker} as a pass-through,
 * which means {@code applyAnswers}'s node transformer — the exact site of the BUG-021 crash —
 * never runs. Those tests pin the permission/HTTP contract; this class instead exercises the
 * <em>real</em> {@link TipTapTreeWalker} and the <em>real</em> {@link ParameterSubstitutor}, so
 * the switch on the node type is genuinely executed. Only the DOCX/PDF exporters are mocked
 * (their bytes are irrelevant here) and the captured body handed to them is asserted instead.</p>
 *
 * <p>Standalone MockMvc slice — no Testcontainers, no Spring context. Follows the same setup as
 * {@link DocumentControllerPermissionsTest}: mocked collaborators, the real
 * {@link GlobalExceptionHandler}, and {@link AuthenticationPrincipalArgumentResolver} backed by
 * the {@code SecurityContextHolder}.</p>
 */
@ExtendWith(MockitoExtension.class)
class DocumentControllerApplyAnswersTest {

    private static final UUID DOC_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID TEMPLATE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000dd");
    private static final String LOGIN = "alice";

    private static final byte[] DOCX_BYTES = {1, 2, 3};
    private static final byte[] PDF_BYTES = {4, 5, 6};

    @Mock private DocumentService documentService;
    @Mock private DocumentPermissionService documentPermissionService;
    @Mock private FolderPermissionService folderPermissionService;
    @Mock private DocumentOwnerResolver ownerResolver;
    @Mock private DocxExportService docxExportService;
    @Mock private PdfExportService pdfExportService;
    @Mock private TemplateService templateService;
    @Mock private NodeResolver nodeResolver;
    // ParameterSubstitutor's own collaborators — mocked so the real substitutor can be constructed.
    @Mock private NumberToWordsService numberToWords;
    @Mock private FioFormatterService fioFormatter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Real walker + real substitutor: applyAnswers' switch is actually executed.
        TipTapTreeWalker treeWalker = new TipTapTreeWalker();
        ParameterSubstitutor substitutor = new ParameterSubstitutor(numberToWords, fioFormatter);

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

    // ── BUG-021: typeless / null-type nodes must not crash export ───────────────

    @Test
    @DisplayName("BUG-021: DOCX export with a node lacking a \"type\" field returns 200 (not 500)")
    void exportDocx_withNodeMissingType_returns200() throws Exception {
        // given — body contains a bare node with no "type" key at all
        Map<String, Object> typelessNode = new LinkedHashMap<>();
        typelessNode.put("text", "typeless");

        givenReadableDocumentWithBody(docWithContent(
                paragraph(List.of(typelessNode))));
        when(docxExportService.export(any(), eq(null))).thenReturn(DOCX_BYTES);

        // when / then — must not throw; exporter runs and its bytes are streamed back
        mockMvc.perform(get("/api/documents/{id}/export/docx", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(content().bytes(DOCX_BYTES));

        // then — the typeless node survived the walk verbatim (fell into default)
        Map<String, Object> resolved = capturedDocxBody();
        assertThat(resolved).isEqualTo(docWithContent(paragraph(List.of(typelessNode))));
    }

    @Test
    @DisplayName("BUG-021: PDF export with a node lacking a \"type\" field returns 200 (not 500)")
    void exportPdf_withNodeMissingType_returns200() throws Exception {
        // given — body contains a bare node with no "type" key at all
        Map<String, Object> typelessNode = new LinkedHashMap<>();
        typelessNode.put("text", "typeless");

        givenReadableDocumentWithBody(docWithContent(
                paragraph(List.of(typelessNode))));
        when(pdfExportService.export(any(), eq(null))).thenReturn(PDF_BYTES);

        // when / then
        mockMvc.perform(get("/api/documents/{id}/export/pdf", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF_BYTES));

        assertThat(capturedPdfBody()).isEqualTo(docWithContent(paragraph(List.of(typelessNode))));
    }

    @Test
    @DisplayName("BUG-021: DOCX export with \"type\": null returns 200 (not 500)")
    void exportDocx_withNullType_returns200() throws Exception {
        // given — explicit "type": null (the case the old `switch ((String) ...)` NPE'd on)
        Map<String, Object> nullTypeNode = nodeWithType(null);

        givenReadableDocumentWithBody(docWithContent(
                paragraph(List.of(nullTypeNode))));
        when(docxExportService.export(any(), eq(null))).thenReturn(DOCX_BYTES);

        // when / then
        mockMvc.perform(get("/api/documents/{id}/export/docx", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(content().bytes(DOCX_BYTES));

        assertThat(capturedDocxBody()).isEqualTo(docWithContent(paragraph(List.of(nullTypeNode))));
    }

    @Test
    @DisplayName("BUG-021: PDF export with \"type\": null returns 200 (not 500)")
    void exportPdf_withNullType_returns200() throws Exception {
        // given
        Map<String, Object> nullTypeNode = nodeWithType(null);

        givenReadableDocumentWithBody(docWithContent(
                paragraph(List.of(nullTypeNode))));
        when(pdfExportService.export(any(), eq(null))).thenReturn(PDF_BYTES);

        // when / then
        mockMvc.perform(get("/api/documents/{id}/export/pdf", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF_BYTES));

        assertThat(capturedPdfBody()).isEqualTo(docWithContent(paragraph(List.of(nullTypeNode))));
    }

    @Test
    @DisplayName("BUG-021: export with the null-type node nested among valid siblings still succeeds")
    void exportDocx_withNullTypeAmongValidSiblings_returns200() throws Exception {
        // given — a typeless node interleaved with a normal text node inside a paragraph
        Map<String, Object> textNode = text("Привет");
        Map<String, Object> typelessNode = new LinkedHashMap<>();
        typelessNode.put("text", "typeless");
        Map<String, Object> parameterField = parameterField("client_name", "ФИО клиента");

        givenReadableDocumentWithBodyAndAnswers(
                docWithContent(paragraph(List.of(textNode, typelessNode, parameterField))),
                Map.of("client_name", "Иван Иванович"));
        // client_name has no parameter definition (empty index) → formatted raw, trimmed
        when(docxExportService.export(any(), eq(null))).thenReturn(DOCX_BYTES);

        // when / then
        mockMvc.perform(get("/api/documents/{id}/export/docx", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(content().bytes(DOCX_BYTES));

        // then — valid siblings still resolved, typeless node preserved
        Map<String, Object> resolved = capturedDocxBody();
        List<Object> paragraphContent = nodeContent(resolved, 0, "content");
        assertThat(paragraphContent).containsExactly(
                textNode,
                typelessNode,
                text("Иван Иванович"));
    }

    // ── BUG-021: same path, but with the REAL exporters (valid bytes, not a mock) ──

    @Test
    @DisplayName("BUG-021: DOCX export of a typeless body returns a real, openable DOCX")
    void exportDocx_withTypelessNode_realExporterProducesValidDocx() throws Exception {
        // given — mocked services/permissions, but the real applyAnswers + real DocxExportServiceImpl
        givenReadableDocumentWithoutResolverStub(docWithContent(
                paragraph(List.of(typelessNode(), text("Договор")))));

        // when
        MvcResult result = realExporterMockMvc()
                .perform(get("/api/documents/{id}/export/docx", DOC_ID))
                .andExpect(status().isOk())
                .andReturn();

        // then — DOCX is an OOXML zip container: "PK\u0003\u0004"
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 2)).isEqualTo("PK");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains(".docx");
    }

    @Test
    @DisplayName("BUG-021: PDF export of a typeless body returns a real, valid PDF")
    void exportPdf_withTypelessNode_realExporterProducesValidPdf() throws Exception {
        // given — a node with "type": null among valid siblings, real exporter
        givenReadableDocumentWithoutResolverStub(docWithContent(
                paragraph(List.of(nodeWithType(null), text("Договор")))));

        // when
        MvcResult result = realExporterMockMvc()
                .perform(get("/api/documents/{id}/export/pdf", DOC_ID))
                .andExpect(status().isOk())
                .andReturn();

        // then — PDF magic bytes
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 4)).isEqualTo("%PDF");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains(".pdf");
    }

    // ── regression: normal parameterField substitution is not weakened ──────────

    @Test
    @DisplayName("parameterField in the body is substituted with the answer value (does not regress)")
    void exportDocx_parameterFieldSubstituted() throws Exception {
        // given — a well-formed body with a parameterField and an answer for it
        givenReadableDocumentWithBodyAndAnswers(
                docWithContent(paragraph(List.of(parameterField("client_name", "ФИО клиента")))),
                Map.of("client_name", "Иван Иванович"));
        when(docxExportService.export(any(), eq(null))).thenReturn(DOCX_BYTES);

        // when / then
        mockMvc.perform(get("/api/documents/{id}/export/docx", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(content().bytes(DOCX_BYTES));

        // then — the parameterField is replaced by a text node carrying the formatted answer
        Map<String, Object> resolved = capturedDocxBody();
        List<Object> paragraphContent = nodeContent(resolved, 0, "content");
        assertThat(paragraphContent).containsExactly(text("Иван Иванович"));
    }

    @Test
    @DisplayName("parameterField with a DATE parameter is formatted via the real substitutor")
    void exportDocx_parameterFieldDateFormatted() throws Exception {
        // given — the real substitutor formats an ISO date to the Russian long form
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.READ))
                .thenReturn(true);
        when(documentService.getById(DOC_ID)).thenReturn(documentWithBodyAndAnswers(
                docWithContent(paragraph(List.of(parameterField("contract_date", "Дата")))),
                Map.of("contract_date", "2026-03-20")));
        when(templateService.getById(TEMPLATE_ID)).thenReturn(templateWithParameters(
                Parameter.builder()
                        .code("contract_date")
                        .label("Дата договора")
                        .type(ParameterType.DATE)
                        .build()));
        when(nodeResolver.buildClauseNumberingMap(any())).thenReturn(Map.of());
        when(docxExportService.export(any(), eq(null))).thenReturn(DOCX_BYTES);

        // when / then
        mockMvc.perform(get("/api/documents/{id}/export/docx", DOC_ID))
                .andExpect(status().isOk());

        // then — DATE / LONG_RU → "20 марта 2026 г."
        Map<String, Object> resolved = capturedDocxBody();
        List<Object> paragraphContent = nodeContent(resolved, 0, "content");
        assertThat(paragraphContent).containsExactly(text("20 марта 2026 г."));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** Permission to read + a document whose body is {@code body}; template metadata resolution fails gracefully. */
    private void givenReadableDocumentWithBody(Map<String, Object> body) {
        givenReadableDocumentWithBodyAndAnswers(body, Map.of());
    }

    /**
     * Same as {@link #givenReadableDocumentWithBody} but <em>without</em> the
     * {@code nodeResolver.buildClauseNumberingMap} stub — required when the real
     * {@link NodeResolver} is wired in, otherwise Mockito reports an unnecessary stubbing.
     */
    private void givenReadableDocumentWithoutResolverStub(Map<String, Object> body) {
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.READ))
                .thenReturn(true);
        when(documentService.getById(DOC_ID)).thenReturn(documentWithBodyAndAnswers(body, Map.of()));
        when(templateService.getById(TEMPLATE_ID))
                .thenThrow(new IllegalStateException("template not needed for this slice"));
    }

    /**
     * Standalone MockMvc wired with the <em>real</em> {@link NodeResolver},
     * {@link DocxExportServiceImpl} and {@link PdfExportServiceImpl} — so a regression is proven on
     * actual export bytes rather than on a mocked exporter.
     */
    private MockMvc realExporterMockMvc() {
        DocumentController controller = new DocumentController(
                documentService,
                documentPermissionService,
                folderPermissionService,
                ownerResolver,
                new DocxExportServiceImpl(),
                new PdfExportServiceImpl(),
                templateService,
                new TipTapTreeWalker(),
                new ParameterSubstitutor(numberToWords, fioFormatter),
                new NodeResolver());
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    private void givenReadableDocumentWithBodyAndAnswers(
            Map<String, Object> body, Map<String, Object> answers) {
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.READ))
                .thenReturn(true);
        when(documentService.getById(DOC_ID)).thenReturn(documentWithBodyAndAnswers(body, answers));
        // No snapshot/template available → empty parameter index and null formatting (defaults).
        when(templateService.getById(TEMPLATE_ID))
                .thenThrow(new IllegalStateException("template not needed for this slice"));
        when(nodeResolver.buildClauseNumberingMap(any())).thenReturn(Map.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedDocxBody() throws IOException {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(docxExportService).export(captor.capture(), eq(null));
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedPdfBody() throws IOException {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(pdfExportService).export(captor.capture(), eq(null));
        return captor.getValue();
    }

    /** Builds a minimal {@link com.docforge.template.dto.TemplateResponse} carrying {@code params}. */
    private static com.docforge.template.dto.TemplateResponse templateWithParameters(Parameter... params) {
        return new com.docforge.template.dto.TemplateResponse(
                TEMPLATE_ID,
                "tpl",
                "Шаблон",
                null,
                null,
                1,
                1,
                0,
                true,
                USER_ID,
                null,
                null,
                null,
                List.of(params),
                null,
                null,
                null,
                null,
                null);
    }

    /** currentBody = body, generatedBody = body, answers as given, no snapshot. */
    private static DocumentResponse documentWithBodyAndAnswers(
            Map<String, Object> body, Map<String, Object> answers) {
        return new DocumentResponse(
                DOC_ID,
                "Договор",
                "DRAFT",
                TEMPLATE_ID,
                null,
                1,
                USER_ID,
                null,
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:00Z"),
                answers,
                body,
                body,
                null);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> nodeContent(Map<String, Object> doc, int index, String key) {
        List<Object> docContent = (List<Object>) doc.get("content");
        Map<String, Object> child = (Map<String, Object>) docContent.get(index);
        return (List<Object>) child.get(key);
    }

    private static Map<String, Object> docWithContent(Map<String, Object>... blocks) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", "doc");
        doc.put("content", new ArrayList<>(List.of(blocks)));
        return doc;
    }

    private static Map<String, Object> paragraph(List<Object> inlines) {
        Map<String, Object> paragraph = new LinkedHashMap<>();
        paragraph.put("type", "paragraph");
        paragraph.put("content", new ArrayList<>(inlines));
        return paragraph;
    }

    private static Map<String, Object> text(String value) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "text");
        node.put("text", value);
        return node;
    }

    private static Map<String, Object> parameterField(String code, String display) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "parameterField");
        node.put("attrs", Map.of("parameterCode", code, "display", display));
        return node;
    }

    private static Map<String, Object> nodeWithType(Object type) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", type);
        node.put("text", "typeless");
        return node;
    }

    /** A node with no {@code "type"} key at all — the BUG-021 trigger. */
    private static Map<String, Object> typelessNode() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("text", "typeless");
        return node;
    }
}
