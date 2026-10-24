package com.docforge.template;

import com.docforge.AbstractIT;
import com.docforge.security.JwtTokenProvider;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.dto.UpdateTemplateRequest;
import com.docforge.template.model.DocumentFormatting;
import com.docforge.template.model.PageMargins;
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
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the ownership scoping of the <em>template export</em>
 * endpoints (BUG-059).
 *
 * <p>Before the fix {@code ExportController} read the template straight from
 * {@code TemplateRepository}, so any authenticated user could export the content of a
 * foreign <em>draft</em> template — the same hole the read endpoints had. The controller
 * now resolves the template through {@code TemplateService.getReadable(...)}:</p>
 * <ol>
 *   <li>a published template is exportable by everybody ({@code 200});</li>
 *   <li>a draft template is exportable by its owner and by an administrator
 *       ({@code 200});</li>
 *   <li>for anybody else the export is refused with
 *       {@code 403 «Нет доступа к шаблону»} — the very same message the read endpoint
 *       uses, so the rules cannot diverge between reading and exporting.</li>
 * </ol>
 *
 * <p>Both formats are checked end-to-end: the DOCX response must be a real ZIP container
 * ({@code PK} magic) and the PDF response a real PDF ({@code %PDF} magic) — a 200 with a
 * broken body would be a silent regression.</p>
 *
 * <p>Exercises the real security chain (JWT → {@code JwtAuthFilter} →
 * {@code UserDetailsServiceImpl}), the real {@code TemplateService} and the real
 * {@code DocxExportService} / {@code PdfExportService}. Three identities are used:
 * {@code userA} (the template owner), {@code userB} (a stranger) and the seeded
 * {@code admin} account ({@code V014__default_admin_user.sql}).</p>
 */
@AutoConfigureMockMvc
class TemplateExportPermissionIT extends AbstractIT {

    /** {@code TemplateServiceImpl.getReadable} — draft template of a stranger. */
    private static final String TEMPLATE_DENIED = "Нет доступа к шаблону";

    /** Magic bytes of a ZIP/OOXML container (DOCX). */
    private static final String ZIP_MAGIC = "PK";

    /** Magic bytes of a PDF document. */
    private static final String PDF_MAGIC = "%PDF";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private TemplateService templateService;
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

    /** Published template owned by {@code userA}. */
    private UUID publishedTemplateId;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        String loginA = "tpl-exp-a-" + suffix;
        String loginB = "tpl-exp-b-" + suffix;

        userAId = createUser(loginA, "Template Export A");
        userBId = createUser(loginB, "Template Export B");
        userAToken = jwtTokenProvider.generateToken(loginA);
        userBToken = jwtTokenProvider.generateToken(loginB);
        // The administrator is a seeded account — its JWT is issued the same way, so the
        // real UserDetailsServiceImpl grants ROLE_ADMIN.
        adminToken = jwtTokenProvider.generateToken("admin");

        // 1) A draft template with a body — only its owner (and admins) may export it.
        TemplateResponse draft = templateService.create(
                userAId,
                new CreateTemplateRequest("BUG-059 Export Draft " + suffix, null, category(), null));
        draftTemplateId = draft.id();
        assertThat(draft.published()).isFalse();
        templateService.update(draftTemplateId, updateRequest(
                "BUG-059 Export Draft " + suffix, body("DRAFT BODY")));

        // 2) A published template with a body — exportable by everybody.
        TemplateResponse published = templateService.create(
                userAId,
                new CreateTemplateRequest("BUG-059 Export Published " + suffix, null, category(), null));
        publishedTemplateId = published.id();
        templateService.update(publishedTemplateId, updateRequest(
                "BUG-059 Export Published " + suffix, body("PUBLISHED BODY")));
        assertThat(templateService.publish(publishedTemplateId).published()).isTrue();
    }

    // ── 1. DOCX export ────────────────────────────────────────────────────────

    @Test
    @DisplayName("DOCX export of a draft template: stranger → 403 «Нет доступа к шаблону», owner → 200 (ZIP), admin → 200")
    void exportDocx_draftTemplate_strangerDenied_ownerAndAdminAllowed() throws Exception {
        // when — a stranger exports somebody else's draft
        mockMvc.perform(post("/api/templates/{id}/export/docx", draftTemplateId)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(TEMPLATE_DENIED));

        // when — the owner exports their own draft
        byte[] ownerBytes = mockMvc.perform(post("/api/templates/{id}/export/docx", draftTemplateId)
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(magic(ownerBytes, 2)).isEqualTo(ZIP_MAGIC);

        // when — the administrator exports the draft
        byte[] adminBytes = mockMvc.perform(post("/api/templates/{id}/export/docx", draftTemplateId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(magic(adminBytes, 2)).isEqualTo(ZIP_MAGIC);
    }

    @Test
    @DisplayName("DOCX export of a published template: a stranger → 200 (ZIP)")
    void exportDocx_publishedTemplate_strangerAllowed() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/templates/{id}/export/docx", publishedTemplateId)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isOk())
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(magic(bytes, 2)).isEqualTo(ZIP_MAGIC);
        assertThat(result.getResponse().getContentType())
                .contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    // ── 2. PDF export ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("PDF export of a draft template: stranger → 403 «Нет доступа к шаблону», owner → 200 (%PDF), admin → 200")
    void exportPdf_draftTemplate_strangerDenied_ownerAndAdminAllowed() throws Exception {
        // when — a stranger exports somebody else's draft
        mockMvc.perform(post("/api/templates/{id}/export/pdf", draftTemplateId)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(TEMPLATE_DENIED));

        // when — the owner exports their own draft
        byte[] ownerBytes = mockMvc.perform(post("/api/templates/{id}/export/pdf", draftTemplateId)
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(magic(ownerBytes, 4)).isEqualTo(PDF_MAGIC);

        // when — the administrator exports the draft
        byte[] adminBytes = mockMvc.perform(post("/api/templates/{id}/export/pdf", draftTemplateId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(magic(adminBytes, 4)).isEqualTo(PDF_MAGIC);
    }

    @Test
    @DisplayName("PDF export of a published template: a stranger → 200 (%PDF)")
    void exportPdf_publishedTemplate_strangerAllowed() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/templates/{id}/export/pdf", publishedTemplateId)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isOk())
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(magic(bytes, 4)).isEqualTo(PDF_MAGIC);
        assertThat(result.getResponse().getContentType()).contains("application/pdf");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * First bytes of the payload as ISO-8859-1 text — the magic signature.
     *
     * @param length how many leading bytes to read (2 for the ZIP magic, 4 for the PDF one)
     */
    private static String magic(byte[] bytes, int length) {
        assertThat(bytes).hasSizeGreaterThanOrEqualTo(length);
        return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
    }

    /** Unique category of the two test templates — isolates them from the dev database. */
    private String category() {
        return "bug059-export-" + suffix;
    }

    /** TipTap body with a single paragraph — enough to render DOCX and PDF. */
    private static Map<String, Object> body(String text) {
        return Map.of(
                "type", "doc",
                "content", List.of(Map.of(
                        "type", "paragraph",
                        "content", List.of(Map.of(
                                "type", "text",
                                "text", text)))));
    }

    private UpdateTemplateRequest updateRequest(String name, Map<String, Object> body) {
        DocumentFormatting formatting = DocumentFormatting.builder()
                .pageSize("A4")
                .orientation("portrait")
                .margins(PageMargins.builder().top(20).right(15).bottom(20).left(30).build())
                .defaultFont("Times New Roman, serif")
                .defaultFontSize(14)
                .lineSpacing(1.5)
                .build();
        return new UpdateTemplateRequest(
                name, null, category(), null, null, body, null, formatting, null, null);
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
