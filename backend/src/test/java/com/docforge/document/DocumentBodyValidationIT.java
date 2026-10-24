package com.docforge.document;

import com.docforge.AbstractIT;
import com.docforge.AuditTestSupport;
import com.docforge.document.model.Document;
import com.docforge.document.model.DocumentRevision;
import com.docforge.document.model.DocumentRevisionBody;
import com.docforge.security.JwtTokenProvider;
import com.docforge.template.TemplateService;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.user.User;
import com.docforge.user.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for BUG-063 — structural validation of {@code current_body}
 * at the write boundary and export robustness against legacy garbage.
 *
 * <p><b>What was broken.</b> {@code PUT /api/documents/{id}/body} accepted any JSON
 * ({@code UpdateBodyRequest(@NotNull Object currentBody)}), so {@code current_body} could
 * hold an array, a string, or a node with a non-object {@code attrs}. The export endpoints
 * then died with {@code ClassCastException} → HTTP 500: the body cast in
 * {@code DocumentController.exportDocx/exportPdf}, the {@code attrs} cast in
 * {@code applyAnswers} and the {@code type} casts in the exporters.</p>
 *
 * <p><b>The fix under test.</b></p>
 * <ol>
 *   <li>Write boundary: {@code DocumentServiceImpl.updateBody} runs
 *       {@link TipTapBodyValidator} before persisting — a malformed body yields
 *       {@code 422} and <em>nothing</em> is written to MongoDB.</li>
 *   <li>Export: a legacy non-object body yields {@code 422} (not 500); nodes with junk
 *       {@code attrs} / non-string {@code parameterCode} / non-string {@code type} are
 *       passed through or left unresolved instead of crashing.</li>
 *   <li>Restore boundary: {@code DocumentServiceImpl.restoreRevision} validates the
 *       revision body before it is written into {@code current_body} — restoring a
 *       structurally invalid revision yields {@code 422} and leaves {@code current_body}
 *       unchanged (the revision is seeded directly through the revision repositories,
 *       past the write boundary).</li>
 *   <li>Exporter tolerance: non-string {@code attrs} values ({@code displayName},
 *       {@code textAlign}, table cell borders, …), non-string {@code text}, marks without
 *       a {@code type} and {@code null} entries in {@code phrasingsRef.displayNames}
 *       no longer throw {@code ClassCastException}/{@code NullPointerException} — the
 *       export returns {@code 200} instead of 500.</li>
 * </ol>
 *
 * <p><b>Why {@code DocumentRepository} seeding.</b> Garbage may already sit in the
 * database (it was written before the validator existed), so the export cases seed
 * {@code current_body} <em>directly through the repository</em>, bypassing the write
 * boundary — exactly the situation the exporters have to survive. Export then runs
 * through the real {@code DocxExportServiceImpl}/{@code PdfExportServiceImpl} and the
 * assertions are made on the produced bytes (DOCX {@code word/document.xml}, PDF magic).</p>
 *
 * <p>Real security chain (JWT → {@code JwtAuthFilter} → {@code UserDetailsServiceImpl}),
 * real services, real exporters. Cleanup removes every row the test creates (see
 * {@code @AfterEach}) because the audit writer is asynchronous
 * ({@link AuditTestSupport}).</p>
 */
@AutoConfigureMockMvc
class DocumentBodyValidationIT extends AbstractIT {

    private static final String VALID_BODY_JSON = """
            {"type":"doc","content":[{"type":"paragraph","content":[
              {"type":"text","text":"Договор"},
              {"type":"parameterField","attrs":{"parameterCode":"client_name","display":"ФИО"}}
            ]}]}""";

    /**
     * Legacy garbage, three flavours at once: an unknown block with string
     * {@code attrs}, an inline text node with string {@code attrs} (must be passed
     * through verbatim) and two unresolvable {@code parameterField} nodes —
     * {@code parameterCode: 42} (non-string) and string {@code attrs} (must fall back
     * to the «—» placeholder instead of resolving / crashing).
     */
    private static final String JUNK_ATTRS_BODY_JSON = """
            {"type":"doc","content":[
              {"type":"customBlock","attrs":"junk"},
              {"type":"paragraph","content":[
                {"type":"text","text":"до"},
                {"type":"text","text":"мусор-attrs","attrs":"junk"},
                {"type":"parameterField","attrs":{"parameterCode":42}},
                {"type":"parameterField","attrs":"junk"},
                {"type":"text","text":"после"}
              ]}
            ]}""";

    /** A block node with a non-string {@code type} must fall through to the default branch. */
    private static final String NUMERIC_TYPE_BODY_JSON = """
            {"type":"doc","content":[
              {"type":42},
              {"type":"paragraph","content":[{"type":"text","text":"ok"}]}
            ]}""";

    /** Regression: a normal body with a resolvable {@code parameterField}. */
    private static final String PARAM_BODY_JSON = """
            {"type":"doc","content":[{"type":"paragraph","content":[
              {"type":"text","text":"Заказчик: "},
              {"type":"parameterField","attrs":{"parameterCode":"client_name","display":"ФИО"}}
            ]}]}""";

    /** Invalid revision body: a string entry inside {@code marks} (BUG-063 marks rules). */
    private static final String REVISION_JUNK_MARKS_BODY_JSON = """
            {"type":"doc","content":[{"type":"paragraph","content":[
              {"type":"text","text":"x","marks":["bold"]}]}]}""";

    /** {@code parameterField} with a non-string {@code attrs.displayName} — legacy garbage. */
    private static final String NON_STRING_DISPLAY_NAME_BODY_JSON = """
            {"type":"doc","content":[{"type":"paragraph","content":[
              {"type":"parameterField","attrs":{"displayName":42}}]}]}""";

    /** Text node with a non-string {@code text}; neighbours must still export. */
    private static final String NON_STRING_TEXT_BODY_JSON = """
            {"type":"doc","content":[{"type":"paragraph","content":[
              {"type":"text","text":"до"},
              {"type":"text","text":42},
              {"type":"text","text":"после"}
            ]}]}""";

    /** Paragraph with a non-string {@code attrs.textAlign}. */
    private static final String NON_STRING_TEXT_ALIGN_BODY_JSON = """
            {"type":"doc","content":[
              {"type":"paragraph","attrs":{"textAlign":42},"content":[{"type":"text","text":"ок"}]}
            ]}""";

    /** Text node with a mark that has no {@code type} at all — the mark must be ignored. */
    private static final String MARK_WITHOUT_TYPE_BODY_JSON = """
            {"type":"doc","content":[{"type":"paragraph","content":[
              {"type":"text","text":"текст-без-типа-метки","marks":[{}]}]}]}""";

    /**
     * Minimal table whose cells carry junk border values: a number ({@code borderTop: 123})
     * and an object ({@code borderLeft: {"x":1}}) — legacy garbage the exporters used to
     * cast to {@code String}.
     */
    private static final String JUNK_TABLE_BORDER_BODY_JSON = """
            {"type":"doc","content":[
              {"type":"table","content":[
                {"type":"tableRow","content":[
                  {"type":"tableCell","attrs":{"borderTop":123},"content":[
                    {"type":"paragraph","content":[{"type":"text","text":"граница-число"}]}]},
                  {"type":"tableCell","attrs":{"borderLeft":{"x":1}},"content":[
                    {"type":"paragraph","content":[{"type":"text","text":"граница-объект"}]}]}
                ]}
              ]},
              {"type":"paragraph","content":[{"type":"text","text":"после-таблицы"}]}
            ]}""";

    /** {@code phrasingsRef} whose {@code displayNames} holds a single {@code null} entry. */
    private static final String PHRASINGS_REF_NULL_DISPLAY_NAME_BODY_JSON = """
            {"type":"doc","content":[{"type":"paragraph","content":[
              {"type":"phrasingsRef","attrs":{"displayNames":[null]}},
              {"type":"text","text":"после-phrasings"}
            ]}]}""";

    private static final String BODY_NOT_AN_OBJECT = "Тело документа должно быть объектом TipTap-дерева";
    /** {@code ParameterSubstitutor.EMPTY_PLACEHOLDER} for an unresolved / empty parameter. */
    private static final String UNRESOLVED_PLACEHOLDER = "—";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private TemplateService templateService;
    @Autowired private DocumentService documentService;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentRevisionRepository revisionRepository;
    @Autowired private DocumentRevisionBodyRepository revisionBodyRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    private UUID ownerId;
    private UUID templateId;
    private UUID documentId;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        String ownerLogin = "body-valid-owner-" + suffix;
        ownerId = createUser(ownerLogin, "Body Validation Owner");
        ownerToken = jwtTokenProvider.generateToken(ownerLogin);

        TemplateResponse template = templateService.create(
                ownerId,
                new CreateTemplateRequest("BodyValidationIT Template " + suffix, null, null, null));
        templateId = template.id();

        documentId = documentService.create(
                template.id(), "BodyValidationIT Doc " + suffix, ownerId, null, null).id();
    }

    @AfterEach
    void tearDown() {
        // MongoDB: тела редакций (их содержимое может быть намеренно мусорным)
        // и документ теста (его тело может быть намеренно мусорным).
        mongoTemplate.remove(Query.query(Criteria.where("document_id").is(documentId)), "document_revisions");
        documentRepository.deleteById(documentId);

        // PostgreSQL: реестры и аудит, затем пользователь вместе с его строками аудита.
        AuditTestSupport.awaitQuiescence(jdbcTemplate,
                "SELECT COUNT(*) FROM audit_log WHERE entity_id IN (?, ?)", documentId, templateId);
        jdbcTemplate.update("DELETE FROM document_revision WHERE document_id = ?", documentId);
        jdbcTemplate.update("DELETE FROM document_registry WHERE template_id = ?", templateId);
        jdbcTemplate.update("DELETE FROM template_version WHERE template_id = ?", templateId);
        jdbcTemplate.update("DELETE FROM audit_log WHERE entity_id IN (?, ?)", documentId, templateId);
        jdbcTemplate.update("DELETE FROM template_registry WHERE id = ?", templateId);
        AuditTestSupport.deleteUserWithAuditRows(jdbcTemplate, ownerId);
    }

    // ── write boundary: PUT /api/documents/{id}/body ──────────────────────────

    @Test
    @DisplayName("PUT body with an array currentBody → 422 and nothing persisted")
    void updateBody_withArrayBody_returns422AndPersistsNothing() throws Exception {
        putBodyAndExpect422("{\"currentBody\":[{\"type\":\"doc\"}]}");

        assertCurrentBodyIsNull();
    }

    @Test
    @DisplayName("PUT body with a string currentBody → 422 and nothing persisted")
    void updateBody_withStringBody_returns422AndPersistsNothing() throws Exception {
        putBodyAndExpect422("{\"currentBody\":\"мусор\"}");

        assertCurrentBodyIsNull();
    }

    @Test
    @DisplayName("PUT body with a number currentBody → 422 and nothing persisted")
    void updateBody_withNumberBody_returns422AndPersistsNothing() throws Exception {
        putBodyAndExpect422("{\"currentBody\":42}");

        assertCurrentBodyIsNull();
    }

    @Test
    @DisplayName("PUT body with a node whose attrs is a string → 422 and nothing persisted")
    void updateBody_withJunkAttrs_returns422AndPersistsNothing() throws Exception {
        putBodyAndExpect422(
                "{\"currentBody\":{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"attrs\":\"junk\"}]}}");

        assertCurrentBodyIsNull();
    }

    @Test
    @DisplayName("PUT body with a node whose content is a string → 422 and nothing persisted")
    void updateBody_withJunkContent_returns422AndPersistsNothing() throws Exception {
        putBodyAndExpect422("{\"currentBody\":{\"type\":\"doc\",\"content\":\"junk\"}}");

        assertCurrentBodyIsNull();
    }

    @Test
    @DisplayName("PUT body with a node whose marks is a string → 422 and nothing persisted")
    void updateBody_withJunkMarks_returns422AndPersistsNothing() throws Exception {
        putBodyAndExpect422("""
                {"currentBody":{"type":"doc","content":[{"type":"paragraph","content":[
                  {"type":"text","text":"x","marks":"junk"}]}]}}""");

        assertCurrentBodyIsNull();
    }

    @Test
    @DisplayName("PUT body with a valid TipTap tree → 204 and the body is persisted")
    void updateBody_withValidBody_returns204AndPersistsBody() throws Exception {
        mockMvc.perform(put("/api/documents/{id}/body", documentId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentBody\":" + VALID_BODY_JSON + "}"))
                .andExpect(status().isNoContent());

        assertStoredBodyMatchesJson(VALID_BODY_JSON);
    }

    @Test
    @DisplayName("A rejected garbage PUT must keep the previously persisted body intact")
    void updateBody_withJunkAfterValid_keepsPreviousBody() throws Exception {
        mockMvc.perform(put("/api/documents/{id}/body", documentId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentBody\":" + VALID_BODY_JSON + "}"))
                .andExpect(status().isNoContent());

        putBodyAndExpect422("{\"currentBody\":[\"мусор\"]}");

        assertStoredBodyMatchesJson(VALID_BODY_JSON);
    }

    // ── restore boundary: POST /api/documents/{id}/revisions/{revNum}/restore ──

    @Test
    @DisplayName("Restore of a revision with marks:[\"bold\"] → 422 and current_body left unchanged")
    void restoreRevision_withJunkMarkEntry_returns422AndKeepsCurrentBody() throws Exception {
        seedCurrentBodyJson(VALID_BODY_JSON);
        seedRevision(1, REVISION_JUNK_MARKS_BODY_JSON);

        mockMvc.perform(post("/api/documents/{id}/revisions/{revNum}/restore", documentId, 1)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.fieldErrors.currentBody")
                        .value("currentBody.content[0].content[0].marks[0]"));

        assertStoredBodyMatchesJson(VALID_BODY_JSON);
    }

    @Test
    @DisplayName("Restore of a revision whose body root is not an object → 422 and current_body left unchanged")
    void restoreRevision_withNonObjectRoot_returns422AndKeepsCurrentBody() throws Exception {
        seedCurrentBodyJson(VALID_BODY_JSON);
        seedRevision(1, "\"мусор\"");

        mockMvc.perform(post("/api/documents/{id}/revisions/{revNum}/restore", documentId, 1)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.fieldErrors.currentBody").value("ожидается JSON-объект"));

        assertStoredBodyMatchesJson(VALID_BODY_JSON);
    }

    @Test
    @DisplayName("Restore of a structurally valid revision still replaces current_body (positive control)")
    void restoreRevision_withValidBody_restoresRevisionBody() throws Exception {
        seedCurrentBodyJson(PARAM_BODY_JSON);
        seedRevision(1, VALID_BODY_JSON);

        mockMvc.perform(post("/api/documents/{id}/revisions/{revNum}/restore", documentId, 1)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        assertStoredBodyMatchesJson(VALID_BODY_JSON);
    }

    // ── export robustness: garbage seeded past the validator ──────────────────

    @Test
    @DisplayName("Export DOCX of a legacy array body → 422, not 500")
    void exportDocx_withArrayBody_returns422() throws Exception {
        seedCurrentBodyJson("[\"мусор\",\"не\",\"тело\"]");

        mockMvc.perform(get("/api/documents/{id}/export/docx", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(BODY_NOT_AN_OBJECT));
    }

    @Test
    @DisplayName("Export PDF of a legacy array body → 422, not 500")
    void exportPdf_withArrayBody_returns422() throws Exception {
        seedCurrentBodyJson("[\"мусор\",\"не\",\"тело\"]");

        mockMvc.perform(get("/api/documents/{id}/export/pdf", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(BODY_NOT_AN_OBJECT));
    }

    @Test
    @DisplayName("Export DOCX of a legacy string body → 422, not 500")
    void exportDocx_withStringBody_returns422() throws Exception {
        seedCurrentBodyJson("\"мусор\"");

        mockMvc.perform(get("/api/documents/{id}/export/docx", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    @DisplayName("DOCX export with junk attrs + numeric parameterCode → 200: nodes pass through, params stay unresolved")
    void exportDocx_withJunkAttrsAndUnresolvedParams_passesThrough_returns200() throws Exception {
        seedCurrentBodyJson(JUNK_ATTRS_BODY_JSON);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/docx", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        String xml = docxDocumentXml(result.getResponse().getContentAsByteArray());
        // «мусор-attrs» — узел с attrs:"junk" прошёл насквозь (не упал и не был потерян)
        assertThat(xml).contains("мусор-attrs").contains("до").contains("после");
        // оба parameterField (parameterCode:42 и attrs:"junk") не разрешились в значение,
        // а отдали плейсхолдер «—»
        assertThat(xml).contains(UNRESOLVED_PLACEHOLDER);
    }

    @Test
    @DisplayName("PDF export with junk attrs + numeric parameterCode → 200, not 500")
    void exportPdf_withJunkAttrsAndUnresolvedParams_passesThrough_returns200() throws Exception {
        seedCurrentBodyJson(JUNK_ATTRS_BODY_JSON);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/pdf", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThatPdf(result);
    }

    @Test
    @DisplayName("DOCX export with a node whose type is a number → 200, not 500")
    void exportDocx_withNumericNodeType_returns200() throws Exception {
        seedCurrentBodyJson(NUMERIC_TYPE_BODY_JSON);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/docx", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        // соседний валидный абзац всё ещё экспортируется
        assertThat(docxDocumentXml(result.getResponse().getContentAsByteArray())).contains("ok");
    }

    @Test
    @DisplayName("PDF export with a node whose type is a number → 200, not 500")
    void exportPdf_withNumericNodeType_returns200() throws Exception {
        seedCurrentBodyJson(NUMERIC_TYPE_BODY_JSON);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/pdf", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThatPdf(result);
    }

    // ── export robustness: non-string attrs values / non-string text ──────────

    @Test
    @DisplayName("DOCX export with parameterField attrs.displayName: 42 → 200, not 500")
    void exportDocx_withNonStringDisplayName_returns200() throws Exception {
        seedCurrentBodyJson(NON_STRING_DISPLAY_NAME_BODY_JSON);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/docx", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        // displayName не является строкой → атрибут игнорируется, параметр остаётся
        // неразрешённым и отдаёт плейсхолдер «—»
        assertThat(docxDocumentXml(result.getResponse().getContentAsByteArray()))
                .contains(UNRESOLVED_PLACEHOLDER);
    }

    @Test
    @DisplayName("PDF export with parameterField attrs.displayName: 42 → 200, not 500")
    void exportPdf_withNonStringDisplayName_returns200() throws Exception {
        seedCurrentBodyJson(NON_STRING_DISPLAY_NAME_BODY_JSON);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/pdf", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThatPdf(result);
    }

    @Test
    @DisplayName("DOCX export with a text node whose text is 42 → 200, neighbours still exported")
    void exportDocx_withNonStringText_returns200() throws Exception {
        seedCurrentBodyJson(NON_STRING_TEXT_BODY_JSON);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/docx", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        String xml = docxDocumentXml(result.getResponse().getContentAsByteArray());
        // не-строковый text превращается в пустой run, соседние текстовые узлы целы
        assertThat(xml).contains("до").contains("после");
    }

    @Test
    @DisplayName("PDF export with a text node whose text is 42 → 200, not 500")
    void exportPdf_withNonStringText_returns200() throws Exception {
        seedCurrentBodyJson(NON_STRING_TEXT_BODY_JSON);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/pdf", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThatPdf(result);
    }

    @Test
    @DisplayName("DOCX export with paragraph attrs.textAlign: 42 → 200, not 500")
    void exportDocx_withNonStringTextAlign_returns200() throws Exception {
        seedCurrentBodyJson(NON_STRING_TEXT_ALIGN_BODY_JSON);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/docx", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        // не-строковый textAlign игнорируется (выравнивание по умолчанию), текст цел
        assertThat(docxDocumentXml(result.getResponse().getContentAsByteArray())).contains("ок");
    }

    @Test
    @DisplayName("PDF export with paragraph attrs.textAlign: 42 → 200, not 500")
    void exportPdf_withNonStringTextAlign_returns200() throws Exception {
        seedCurrentBodyJson(NON_STRING_TEXT_ALIGN_BODY_JSON);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/pdf", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThatPdf(result);
    }

    // ── export robustness: marks without type, junk table borders, phrasingsRef ──

    @Test
    @DisplayName("DOCX export with a mark without type → 200, the marked text survives")
    void exportDocx_withMarkWithoutType_returns200() throws Exception {
        seedCurrentBodyJson(MARK_WITHOUT_TYPE_BODY_JSON);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/docx", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        // метка без type игнорируется (не NPE на switch), сам текст остаётся в документе
        assertThat(docxDocumentXml(result.getResponse().getContentAsByteArray()))
                .contains("текст-без-типа-метки");
    }

    @Test
    @DisplayName("PDF export with a mark without type → 200, the marked text survives")
    void exportPdf_withMarkWithoutType_returns200() throws Exception {
        seedCurrentBodyJson(MARK_WITHOUT_TYPE_BODY_JSON);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/pdf", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThatPdf(result);
    }

    @Test
    @DisplayName("DOCX export with junk table cell borders (number / object) → 200, cells survive")
    void exportDocx_withJunkTableCellBorders_returns200() throws Exception {
        seedCurrentBodyJson(JUNK_TABLE_BORDER_BODY_JSON);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/docx", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        // не-строковые значения границ игнорируются, содержимое ячеек и текст после таблицы целы
        String xml = docxDocumentXml(result.getResponse().getContentAsByteArray());
        assertThat(xml).contains("граница-число").contains("граница-объект").contains("после-таблицы");
    }

    @Test
    @DisplayName("PDF export with junk table cell borders (number / object) → 200, not 500")
    void exportPdf_withJunkTableCellBorders_returns200() throws Exception {
        seedCurrentBodyJson(JUNK_TABLE_BORDER_BODY_JSON);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/pdf", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThatPdf(result);
    }

    @Test
    @DisplayName("DOCX export with phrasingsRef displayNames:[null] → 200, label falls back to «?»")
    void exportDocx_withPhrasingsRefNullDisplayName_returns200() throws Exception {
        seedCurrentBodyJson(PHRASINGS_REF_NULL_DISPLAY_NAME_BODY_JSON);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/docx", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        // null-элемент displayNames отбрасывается, список пуст → legacy-fallback «?»
        String xml = docxDocumentXml(result.getResponse().getContentAsByteArray());
        assertThat(xml).contains("[?]").contains("после-phrasings");
    }

    @Test
    @DisplayName("PDF export with phrasingsRef displayNames:[null] → 200, not 500")
    void exportPdf_withPhrasingsRefNullDisplayName_returns200() throws Exception {
        seedCurrentBodyJson(PHRASINGS_REF_NULL_DISPLAY_NAME_BODY_JSON);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/pdf", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThatPdf(result);
    }

    // ── regression: normal parameter substitution still works ─────────────────

    @Test
    @DisplayName("DOCX export of a normal body with parameterField still substitutes the answer (200)")
    void exportDocx_withParameterField_substitutesAnswers_returns200() throws Exception {
        seedCurrentBodyJson(PARAM_BODY_JSON);
        seedAnswersJson("{\"client_name\":\"Иван Иванович\"}");

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/docx", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        String xml = docxDocumentXml(result.getResponse().getContentAsByteArray());
        assertThat(xml).contains("Заказчик:").contains("Иван Иванович");
    }

    @Test
    @DisplayName("PDF export of a normal body with parameterField still substitutes the answer (200)")
    void exportPdf_withParameterField_substitutesAnswers_returns200() throws Exception {
        seedCurrentBodyJson(PARAM_BODY_JSON);
        seedAnswersJson("{\"client_name\":\"Иван Иванович\"}");

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/export/pdf", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThatPdf(result);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void putBodyAndExpect422(String rawJson) throws Exception {
        mockMvc.perform(put("/api/documents/{id}/body", documentId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.fieldErrors.currentBody").isNotEmpty());
    }

    private void assertCurrentBodyIsNull() {
        Object stored = documentRepository.findById(documentId).orElseThrow().getCurrentBody();
        assertThat(stored).isNull();
    }

    /** Сравнивает сохранённое тело с ожидаемым JSON без привязки к типам Map/List из драйвера. */
    private void assertStoredBodyMatchesJson(String expectedJson) throws Exception {
        Object stored = documentRepository.findById(documentId).orElseThrow().getCurrentBody();
        JsonNode storedTree = objectMapper.valueToTree(stored);
        assertThat(storedTree).isEqualTo(objectMapper.readTree(expectedJson));
    }

    /**
     * Кладёт произвольный JSON в {@code current_body} в обход валидации — так в базе
     * оказалось легаси-мусорное содержимое, с которым имеет дело экспорт.
     */
    private void seedCurrentBodyJson(String rawJson) throws Exception {
        Document doc = documentRepository.findById(documentId).orElseThrow();
        doc.setCurrentBody(objectMapper.readValue(rawJson, Object.class));
        documentRepository.save(doc);
    }

    /**
     * Кладёт ревизию с произвольным телом в обход валидации (PostgreSQL + MongoDB) —
     * так в истории оказались редакции с мусорным телом, которые нельзя восстанавливать.
     * {@code document_revision.id} и {@code document_revisions._id} совпадают (контракт
     * {@code DocumentServiceImpl#getRevisionBody}), поэтому id выдаёт JPA-сторона.
     */
    private void seedRevision(int revisionNumber, String rawBodyJson) throws Exception {
        DocumentRevision revision = new DocumentRevision();
        revision.setDocumentId(documentId);
        revision.setRevisionNumber(revisionNumber);
        revision.setSavedBy(ownerId);
        revision.setComment("seeded revision " + revisionNumber);
        revision = revisionRepository.save(revision);

        revisionBodyRepository.save(DocumentRevisionBody.builder()
                .id(revision.getId())
                .documentId(documentId)
                .revisionNumber(revisionNumber)
                .body(objectMapper.readValue(rawBodyJson, Object.class))
                .savedBy(ownerId)
                .savedAt(Instant.now())
                .comment("seeded revision " + revisionNumber)
                .build());
    }

    private void seedAnswersJson(String rawJson) throws Exception {
        Document doc = documentRepository.findById(documentId).orElseThrow();
        doc.setAnswers(objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {}));
        documentRepository.save(doc);
    }

    private void assertThatPdf(MvcResult result) {
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    private static String docxDocumentXml(byte[] docx) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("word/document.xml отсутствует в DOCX");
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
}
