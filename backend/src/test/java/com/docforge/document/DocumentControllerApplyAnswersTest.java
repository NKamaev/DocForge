package com.docforge.document;

import com.docforge.common.exception.GlobalExceptionHandler;
import com.docforge.common.format.FioFormatterService;
import com.docforge.common.format.NumberToWordsService;
import com.docforge.document.dto.DocumentResponse;
import com.docforge.document.generator.ConditionEvaluator;
import com.docforge.document.generator.ConditionalNodeFilter;
import com.docforge.document.generator.ParameterSubstitutor;
import com.docforge.document.generator.PythonConditionEvaluator;
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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
 *
 * <h2>BUG-019 — hidden-by-condition content must never reach the exporter</h2>
 *
 * <p>Since the BUG-019 fix {@code applyAnswers()} first runs
 * {@link com.docforge.document.generator.ConditionalNodeFilter} over the body (dropping /
 * unwrapping {@code conditionalBlock}, {@code conditionalInline} and {@code numberedClause}
 * according to their conditions) and only then builds clause numbering and substitutes
 * parameters. The {@code BUG-019} cases below assert on the map handed to the mocked exporter
 * (the same style as the BUG-021 cases) and, for DOCX, on the real {@code word/document.xml}
 * bytes produced by {@link DocxExportServiceImpl}.</p>
 *
 * <p>The nested cases ({@code ...NestedFalse...}) pin the fix's recursion: a conditional node that
 * only becomes the direct replacement of an unwrapped conditional node must itself be evaluated,
 * otherwise the inner hidden text reaches the exporter (residual leak found after the first
 * BUG-019 fix).</p>
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
    // Python-оценщик мокается: GraalPython на тестовой JVM не нужен, BUG-019-тесты
    // используют simple-условия. Реальный фильтр — чтобы applyAnswers действительно
    // вырезал скрытые узлы перед экспортом.
    @Mock private PythonConditionEvaluator pythonConditionEvaluator;
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
                conditionalNodeFilter(),
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

    // ── BUG-019: content hidden by conditions must not reach the exporter ───────

    @Test
    @DisplayName("BUG-019: a false conditionalBlock reaches the DOCX exporter without its hidden text")
    void exportDocx_withFalseConditionalBlock_bodyHasNoHiddenText() throws Exception {
        // given — body = [скрытый блок, видимый абзац], has_penalty == false
        givenReadableDocumentWithBodyAndAnswers(
                docWithContent(
                        conditionalBlock("has_penalty == true",
                                paragraph(List.of(text("HIDDEN_BLOCK_TEXT")))),
                        paragraph(List.of(text("VISIBLE_TEXT")))),
                Map.of("has_penalty", false));
        when(docxExportService.export(any(), eq(null))).thenReturn(DOCX_BYTES);

        // when / then
        mockMvc.perform(get("/api/documents/{id}/export/docx", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(content().bytes(DOCX_BYTES));

        // then — блок не только без текста: от него не осталось узла
        Map<String, Object> resolved = capturedDocxBody();
        assertThat(collectTexts(resolved)).containsExactly("VISIBLE_TEXT");
        assertThat(docChildTypes(resolved)).containsExactly("paragraph");
    }

    @Test
    @DisplayName("BUG-019: a false conditionalInline reaches the PDF exporter without its hidden text")
    void exportPdf_withFalseConditionalInline_bodyHasNoHiddenText() throws Exception {
        // given — скрытый inline-фрагмент внутри абзаца
        givenReadableDocumentWithBodyAndAnswers(
                docWithContent(paragraph(List.of(
                        text("Начало "),
                        conditionalInline("has_penalty == true", text("HIDDEN_INLINE_TEXT")),
                        text(" конец")))),
                Map.of("has_penalty", false));
        when(pdfExportService.export(any(), eq(null))).thenReturn(PDF_BYTES);

        // when / then
        mockMvc.perform(get("/api/documents/{id}/export/pdf", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF_BYTES));

        // then — соседи по абзацу сохранены, скрытого фрагмента нет
        Map<String, Object> resolved = capturedPdfBody();
        assertThat(collectTexts(resolved)).containsExactly("Начало ", " конец");
        assertThat(nodeContent(resolved, 0, "content"))
                .containsExactly(text("Начало "), text(" конец"));
    }

    @Test
    @DisplayName("BUG-019: a true conditionalBlock is unwrapped — its text stays in the exported body")
    void exportDocx_withTrueConditionalBlock_keepsBlockContent() throws Exception {
        // given — has_penalty == true → блок виден и должен быть развёрнут
        givenReadableDocumentWithBodyAndAnswers(
                docWithContent(conditionalBlock("has_penalty == true",
                        paragraph(List.of(text("VISIBLE_BLOCK_TEXT"))))),
                Map.of("has_penalty", true));
        when(docxExportService.export(any(), eq(null))).thenReturn(DOCX_BYTES);

        // when / then
        mockMvc.perform(get("/api/documents/{id}/export/docx", DOC_ID))
                .andExpect(status().isOk());

        // then — экспортёр получает обычный paragraph, а не узел conditionalBlock
        Map<String, Object> resolved = capturedDocxBody();
        assertThat(collectTexts(resolved)).containsExactly("VISIBLE_BLOCK_TEXT");
        assertThat(docChildTypes(resolved)).containsExactly("paragraph");
    }

    @Test
    @DisplayName("BUG-019: the clause numbering map is built from the filtered tree")
    void exportDocx_withHiddenNumberedClause_numberingMapBuiltFromFilteredTree() throws Exception {
        // given — на вход NodeResolver попадает уже отфильтрованное дерево:
        // скрытого пункта в нём нет. Итоговые номера (скрытый пункт не занимает
        // номер) закреплены в BUG-065-тесте ниже.
        givenReadableDocumentWithBodyAndAnswers(
                docWithContent(
                        numberedClause("has_penalty == true", "clause-1",
                                paragraph(List.of(text("HIDDEN_CLAUSE_TEXT")))),
                        numberedClause(null, "clause-2",
                                paragraph(List.of(text("VISIBLE_CLAUSE_TEXT"))))),
                Map.of("has_penalty", false));
        when(docxExportService.export(any(), eq(null))).thenReturn(DOCX_BYTES);

        // when / then
        mockMvc.perform(get("/api/documents/{id}/export/docx", DOC_ID))
                .andExpect(status().isOk());

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(nodeResolver).buildClauseNumberingMap(captor.capture());
        assertThat(collectTexts(captor.getValue())).containsExactly("VISIBLE_CLAUSE_TEXT");
    }

    // ── BUG-065: clauseRef читает attrs.refId, пункт идентифицируется attrs.id ──

    @Test
    @DisplayName("BUG-065: refs (attrs.refId) resolve against attrs.id and a hidden clause takes no number")
    void exportDocx_withHiddenClauseBetweenVisibleRefs_refsSkipTheHiddenNumber() throws Exception {
        // given — реальный NodeResolver (не мок): пункты c1, c2, c3(скрыт), c4
        // и ссылки attrs.refId на c1, c2, c4. Если бы скрытый c3 занимал номер,
        // последняя ссылка была бы «п. 4», если бы атрибуты не читались — «п. ?».
        givenReadableDocumentWithAnswersAndNoResolverStub(
                docWithContent(
                        numberedClause(null, "c1", paragraph(List.of(text("CLAUSE_1")))),
                        numberedClause(null, "c2", paragraph(List.of(text("CLAUSE_2")))),
                        numberedClause("has_penalty == true", "c3",
                                paragraph(List.of(text("HIDDEN_CLAUSE_3")))),
                        numberedClause(null, "c4", paragraph(List.of(
                                text("ref1="), clauseRef("c1"),
                                text(" ref2="), clauseRef("c2"),
                                text(" ref4="), clauseRef("c4"))))),
                Map.of("has_penalty", false));
        when(docxExportService.export(any(), eq(null))).thenReturn(DOCX_BYTES);

        // when / then
        realResolverMockMvc()
                .perform(get("/api/documents/{id}/export/docx", DOC_ID))
                .andExpect(status().isOk());

        // then — ref1=п. 1., ref2=п. 2., ref4=п. 3.: скрытый c3 не занимает номер;
        // метка — в формате редактора с завершающей точкой (BUG-066)
        Map<String, Object> resolved = capturedDocxBody();
        assertThat(collectTexts(resolved)).containsExactly(
                "CLAUSE_1", "CLAUSE_2",
                "ref1=", "п. 1.", " ref2=", "п. 2.", " ref4=", "п. 3.");
    }

    // ── BUG-019 residual: nested conditionals must be re-evaluated after unwrapping ─

    @Test
    @DisplayName("BUG-019: a nested false block inside a true block does not reach the DOCX exporter")
    void exportDocx_withNestedFalseBlockInsideTrueBlock_bodyHasNoHiddenText() throws Exception {
        // given — внешний блок виден, вложенный скрыт. Развёрнутый внешний блок поднимает
        // вложенный узел на своё место, и именно там он раньше «проскакивал» в файл.
        givenReadableDocumentWithBodyAndAnswers(
                docWithContent(
                        conditionalBlock("has_penalty == true",
                                paragraph(List.of(text("OUTER_VISIBLE_TEXT"))),
                                conditionalBlock("penalty_percent > 10",
                                        paragraph(List.of(text("INNER_HIDDEN_TEXT")))))),
                Map.of("has_penalty", true, "penalty_percent", 5));
        when(docxExportService.export(any(), eq(null))).thenReturn(DOCX_BYTES);

        // when / then
        mockMvc.perform(get("/api/documents/{id}/export/docx", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(content().bytes(DOCX_BYTES));

        // then — экспортёр получает только видимый абзац; узлов-условий в теле нет
        Map<String, Object> resolved = capturedDocxBody();
        assertThat(collectTexts(resolved)).containsExactly("OUTER_VISIBLE_TEXT");
        assertThat(docChildTypes(resolved)).containsExactly("paragraph");
    }

    @Test
    @DisplayName("BUG-019: a nested false inline inside a true inline does not reach the PDF exporter")
    void exportPdf_withNestedFalseInlineInsideTrueInline_bodyHasNoHiddenText() throws Exception {
        // given — тот же сценарий на inline-уровне внутри абзаца
        givenReadableDocumentWithBodyAndAnswers(
                docWithContent(paragraph(List.of(
                        text("BEFORE "),
                        conditionalInline("has_penalty == true",
                                text("INLINE_VISIBLE"),
                                conditionalInline("penalty_percent > 10", text("INLINE_HIDDEN"))),
                        text(" AFTER")))),
                Map.of("has_penalty", true, "penalty_percent", 5));
        when(pdfExportService.export(any(), eq(null))).thenReturn(PDF_BYTES);

        // when / then
        mockMvc.perform(get("/api/documents/{id}/export/pdf", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF_BYTES));

        // then — порядок inline-соседей сохранён, скрытого фрагмента нет
        Map<String, Object> resolved = capturedPdfBody();
        assertThat(nodeContent(resolved, 0, "content")).containsExactly(
                text("BEFORE "), text("INLINE_VISIBLE"), text(" AFTER"));
        assertThat(collectTexts(resolved)).containsExactly("BEFORE ", "INLINE_VISIBLE", " AFTER");
    }

    // ── BUG-019: same path, but with the REAL exporters (bytes, not a mock) ─────

    @Test
    @DisplayName("BUG-019: real DOCX export of a false conditionalBlock omits the hidden text")
    void exportDocx_withFalseConditionalBlock_realDocxHasNoHiddenText() throws Exception {
        // given — реальный applyAnswers + реальный DocxExportServiceImpl
        givenReadableDocumentWithAnswersAndNoResolverStub(
                docWithContent(
                        conditionalBlock("has_penalty == true",
                                paragraph(List.of(text("HIDDEN_BLOCK_TEXT")))),
                        paragraph(List.of(text("VISIBLE_TEXT")))),
                Map.of("has_penalty", false));

        // when
        MvcResult result = realExporterMockMvc()
                .perform(get("/api/documents/{id}/export/docx", DOC_ID))
                .andExpect(status().isOk())
                .andReturn();

        // then — смотрим в word/document.xml: скрытого текста там нет
        String documentXml = docxDocumentXml(result.getResponse().getContentAsByteArray());
        assertThat(documentXml).contains("VISIBLE_TEXT").doesNotContain("HIDDEN_BLOCK_TEXT");
    }

    @Test
    @DisplayName("BUG-019: real DOCX export of a nested false block omits only the inner text")
    void exportDocx_withNestedFalseBlock_realDocxHasNoHiddenText() throws Exception {
        // given — реальный applyAnswers + реальный DocxExportServiceImpl; внешний блок
        // виден, вложенный скрыт
        givenReadableDocumentWithAnswersAndNoResolverStub(
                docWithContent(
                        conditionalBlock("has_penalty == true",
                                paragraph(List.of(text("OUTER_VISIBLE"))),
                                conditionalBlock("penalty_percent > 10",
                                        paragraph(List.of(text("INNER_HIDDEN")))))),
                Map.of("has_penalty", true, "penalty_percent", 5));

        // when
        MvcResult result = realExporterMockMvc()
                .perform(get("/api/documents/{id}/export/docx", DOC_ID))
                .andExpect(status().isOk())
                .andReturn();

        // then — word/document.xml содержит внешний текст и не содержит вложенного
        String documentXml = docxDocumentXml(result.getResponse().getContentAsByteArray());
        assertThat(documentXml).contains("OUTER_VISIBLE").doesNotContain("INNER_HIDDEN");
    }

    @Test
    @DisplayName("BUG-019: real DOCX export of a true conditionalBlock keeps the unwrapped text")
    void exportDocx_withTrueConditionalBlock_realDocxKeepsUnwrappedText() throws Exception {
        // given
        givenReadableDocumentWithAnswersAndNoResolverStub(
                docWithContent(conditionalBlock("has_penalty == true",
                        paragraph(List.of(text("VISIBLE_BLOCK_TEXT"))))),
                Map.of("has_penalty", true));

        // when
        MvcResult result = realExporterMockMvc()
                .perform(get("/api/documents/{id}/export/docx", DOC_ID))
                .andExpect(status().isOk())
                .andReturn();

        // then
        assertThat(docxDocumentXml(result.getResponse().getContentAsByteArray()))
                .contains("VISIBLE_BLOCK_TEXT");
    }

    @Test
    @DisplayName("BUG-019: real PDF export of a false conditionalInline omits the hidden text")
    void exportPdf_withFalseConditionalInline_realPdfHasNoHiddenText() throws Exception {
        // given — ASCII-метки: PDF-текст извлекается надёжно, а кириллица в подмножестве шрифта нет
        givenReadableDocumentWithAnswersAndNoResolverStub(
                docWithContent(paragraph(List.of(
                        text("BEFORE "),
                        conditionalInline("has_penalty == true", text("HIDDEN_INLINE_TEXT")),
                        text(" AFTER")))),
                Map.of("has_penalty", false));

        // when
        MvcResult result = realExporterMockMvc()
                .perform(get("/api/documents/{id}/export/pdf", DOC_ID))
                .andExpect(status().isOk())
                .andReturn();

        // then — в реальном PDF остались только видимые соседи
        String text = pdfText(result.getResponse().getContentAsByteArray());
        assertThat(text).contains("BEFORE").contains("AFTER").doesNotContain("HIDDEN_INLINE_TEXT");
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
                conditionalNodeFilter(),
                new ParameterSubstitutor(numberToWords, fioFormatter),
                new NodeResolver());
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    /**
     * Standalone MockMvc with the <em>real</em> {@link NodeResolver} (BUG-065: the numbering map
     * is keyed by {@code attrs.id} and refs read {@code attrs.refId}) but the mocked exporters —
     * so the resolved body handed to the exporter can be asserted node by node.
     */
    private MockMvc realResolverMockMvc() {
        DocumentController controller = new DocumentController(
                documentService,
                documentPermissionService,
                folderPermissionService,
                ownerResolver,
                docxExportService,
                pdfExportService,
                templateService,
                new TipTapTreeWalker(),
                conditionalNodeFilter(),
                new ParameterSubstitutor(numberToWords, fioFormatter),
                new NodeResolver());
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    /**
     * Real {@link ConditionalNodeFilter} (it walks the tree itself — no walker argument since the
     * BUG-019 fix) with the real {@link ConditionEvaluator}; only the GraalPython-backed evaluator
     * is mocked. A real filter is required here — BUG-019 lives precisely in the interaction
     * «filter → numbering → substitution» inside {@code applyAnswers}.
     */
    private ConditionalNodeFilter conditionalNodeFilter() {
        return new ConditionalNodeFilter(new ConditionEvaluator(), pythonConditionEvaluator);
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

    /**
     * Same as {@link #givenReadableDocumentWithBodyAndAnswers} but <em>without</em> the
     * {@code nodeResolver.buildClauseNumberingMap} stub — required when the real
     * {@link NodeResolver} is wired in. Keeps explicit answers so conditions are evaluated
     * against real values (BUG-019).
     */
    private void givenReadableDocumentWithAnswersAndNoResolverStub(
            Map<String, Object> body, Map<String, Object> answers) {
        when(ownerResolver.resolve(LOGIN)).thenReturn(USER_ID);
        when(documentPermissionService.hasPermission(DOC_ID, USER_ID, DocumentPermission.READ))
                .thenReturn(true);
        when(documentService.getById(DOC_ID)).thenReturn(documentWithBodyAndAnswers(body, answers));
        when(templateService.getById(TEMPLATE_ID))
                .thenThrow(new IllegalStateException("template not needed for this slice"));
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

    // ── BUG-019 node builders ──────────────────────────────────────────────────

    private static Map<String, Object> simpleCondition(String expression) {
        return Map.of("type", "simple", "expression", expression);
    }

    private static Map<String, Object> conditionalBlock(
            String expression, Map<String, Object>... children) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "conditionalBlock");
        node.put("attrs", Map.of(
                "condition", simpleCondition(expression),
                "label", "Блок неустойки"));
        node.put("content", new ArrayList<Object>(List.of(children)));
        return node;
    }

    private static Map<String, Object> conditionalInline(
            String expression, Map<String, Object>... children) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "conditionalInline");
        node.put("attrs", Map.of(
                "condition", simpleCondition(expression),
                "label", "Условный текст"));
        node.put("content", new ArrayList<Object>(List.of(children)));
        return node;
    }

    /** {@code expression == null} → пункт без условия (показывать всегда). */
    private static Map<String, Object> numberedClause(
            String expression, String id, Map<String, Object>... children) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("level", 1);
        attrs.put("numberingStyle", "decimal");
        attrs.put("id", id);
        if (expression != null) attrs.put("condition", simpleCondition(expression));

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "numberedClause");
        node.put("attrs", attrs);
        node.put("content", new ArrayList<Object>(List.of(children)));
        return node;
    }

    /** Ссылка на пункт в реальной TipTap-схеме — цель в {@code attrs.refId} (BUG-065). */
    private static Map<String, Object> clauseRef(String refId) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "clauseRef");
        node.put("attrs", Map.of("refId", refId));
        return node;
    }

    /** Все тексты дерева в порядке обхода — «скрытого текста в теле нет». */
    private static List<String> collectTexts(Map<String, Object> node) {
        List<String> texts = new ArrayList<>();
        collectTexts(node, texts);
        return texts;
    }

    @SuppressWarnings("unchecked")
    private static void collectTexts(Map<String, Object> node, List<String> out) {
        if (node.get("text") instanceof String value) {
            out.add(value);
        }
        if (node.get("content") instanceof List<?> children) {
            for (Object child : children) {
                if (child instanceof Map<?, ?> childMap) {
                    collectTexts((Map<String, Object>) childMap, out);
                }
            }
        }
    }

    private static List<Object> docChildTypes(Map<String, Object> doc) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) doc.get("content");
        List<Object> types = new ArrayList<>();
        for (Map<String, Object> child : children) {
            types.add(child.get("type"));
        }
        return types;
    }

    /** Extracts {@code word/document.xml} from a DOCX (OOXML zip) container. */
    private static String docxDocumentXml(byte[] docx) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("word/document.xml not found in the DOCX archive");
    }

    /** Extracts the text layer of a PDF (ASCII labels only — no font-subset issues). */
    private static String pdfText(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document);
        }
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
