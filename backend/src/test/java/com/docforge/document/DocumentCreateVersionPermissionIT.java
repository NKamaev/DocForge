package com.docforge.document;

import com.docforge.AbstractIT;
import com.docforge.document.model.Document;
import com.docforge.security.JwtTokenProvider;
import com.docforge.template.TemplateService;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.SaveVersionRequest;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.dto.TemplateVersionDTO;
import com.docforge.template.model.Parameter;
import com.docforge.template.model.ParameterType;
import com.docforge.template.model.Question;
import com.docforge.template.model.Questionnaire;
import com.docforge.template.model.QuestionnairePage;
import com.docforge.template.model.Template;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the version gate on {@code POST /api/documents}
 * (fix for BUG-058).
 *
 * <p>Before the fix any authenticated user could instantiate <em>unpublished</em> template
 * content: the {@code templateVersionNumber} request field was passed straight to
 * {@code getSnapshot(...)} without any check, and the implicit version resolution used
 * {@code findLatestSnapshot(...)} — which returns the newest snapshot <em>including</em>
 * drafts created by {@code saveVersion(MINOR/MAJOR)}.</p>
 *
 * <p>The fix enforces:</p>
 * <ol>
 *   <li>an explicit {@code templateVersionNumber} must exist
 *       ({@code 404 «Снимок шаблона не найден»} otherwise) and is accepted for a non-owner
 *       only if the version is <strong>published</strong> — i.e. it was not created by
 *       {@code saveVersion(MINOR/MAJOR)} ({@code 403 «Нет доступа к черновой версии шаблона»});
 *       the owner may use any of their own versions;</li>
 *   <li>an explicit version whose MongoDB snapshot was deleted is a hard error
 *       ({@code 404 «Снимок версии шаблона не найден»}) — the document is <em>not</em>
 *       silently created unpinned, because the caller explicitly asked for that version;</li>
 *   <li>without an explicit version a non-owner is pinned to the latest
 *       <strong>published</strong> snapshot ({@code findLatestPublishedSnapshot}), while the
 *       owner is pinned to the latest snapshot of their own template (drafts included);</li>
 *   <li>legacy {@code template_version} rows without {@code bump_type} (created before the
 *       column existed) count as published;</li>
 *   <li>a {@code ROLLBACK} version counts as published only <em>by provenance</em>: when the
 *       version it was rolled back from ({@code rollback_from}) is itself published. A rollback
 *       of a draft stays a draft and is invisible to non-owners;</li>
 *   <li>if version rows exist but <em>none</em> of them is published, a non-owner gets
 *       {@code 403 «Нет опубликованной версии шаблона»} — the live template must not be used
 *       as a fallback, because after a rollback it may carry draft content;</li>
 *   <li>if the template has <em>no</em> version rows at all (published before versioning
 *       existed), the non-owner falls back to the live template: the document is created
 *       <strong>unpinned</strong> (201).</li>
 * </ol>
 *
 * <p>Denied paths must not persist anything — neither in PostgreSQL
 * ({@code document_registry}) nor in MongoDB ({@code documents}).</p>
 *
 * <p>Exercises the real security chain (JWT → {@code JwtAuthFilter} →
 * {@code UserDetailsServiceImpl} → {@code DocumentOwnerResolver}), the real
 * {@code TemplateService} (versioning included) and the real
 * {@code DocumentServiceImpl}. Two persisted users are used: {@code userA} (the caller /
 * non-owner) and {@code userB} (the template owner).</p>
 *
 * <p>NB: the “unpublished template → 403 «Нет доступа к шаблону»” gate is <em>not</em>
 * duplicated here — it is covered by {@code DocumentCreatePermissionIT}
 * ({@code create_foreignUnpublishedTemplate_returns403AndNoRow}), which fixes the same
 * endpoint and the same message.</p>
 */
@AutoConfigureMockMvc
class DocumentCreateVersionPermissionIT extends AbstractIT {

    /** Message thrown by {@code DocumentServiceImpl.create} for a non-owner draft version. */
    private static final String DRAFT_VERSION_DENIED = "Нет доступа к черновой версии шаблона";

    /** Message thrown by {@code DocumentServiceImpl.create} for an unknown version number. */
    private static final String SNAPSHOT_NOT_FOUND = "Снимок шаблона не найден";

    /**
     * Message thrown by {@code DocumentServiceImpl.create} when the version row exists
     * (and is allowed), but its MongoDB snapshot was deleted.
     */
    private static final String VERSION_SNAPSHOT_NOT_FOUND = "Снимок версии шаблона не найден";

    /**
     * Message thrown by {@code DocumentServiceImpl.create} for a non-owner when the template
     * has version rows, but none of them is published.
     */
    private static final String NO_PUBLISHED_VERSION = "Нет опубликованной версии шаблона";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private DocumentRegistryRepository documentRegistryRepository;
    @Autowired private TemplateService templateService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MongoTemplate mongoTemplate;

    private String suffix;

    /** Caller (non-owner of the template). */
    private UUID userAId;
    private String userAToken;

    /** Owner of the template. */
    private UUID userBId;
    private String userBToken;

    /** Template owned by {@code userB}; published in {@link #setUp()}. */
    private UUID templateId;

    /** Version number of the PUBLISH version created in {@link #setUp()}. */
    private int publishedVersionNumber;

    /** {@code template_version.id} of the PUBLISH version (= the snapshot id). */
    private UUID publishedSnapshotId;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        String loginA = "ver-perm-a-" + suffix;
        String loginB = "ver-perm-b-" + suffix;

        userAId = createUser(loginA, "Ver Perm A");
        userBId = createUser(loginB, "Ver Perm B");
        userAToken = jwtTokenProvider.generateToken(loginA);
        userBToken = jwtTokenProvider.generateToken(loginB);

        // B owns the template and publishes it → one PUBLISH version (bumpType = "PUBLISH").
        TemplateResponse created = templateService.create(
                userBId,
                new CreateTemplateRequest("BUG-058 Template " + suffix, null, null, null));
        templateId = created.id();
        assertThat(created.published()).isFalse();

        TemplateResponse published = templateService.publish(templateId);
        assertThat(published.published()).isTrue();

        TemplateVersionDTO publishVersion = templateService.listVersions(templateId).stream()
                .filter(v -> "PUBLISH".equals(v.bumpType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("PUBLISH version was not created"));
        publishedVersionNumber = publishVersion.versionNumber();
        publishedSnapshotId = publishVersion.id();
    }

    // ── 1. non-owner + explicit draft version → 403 ───────────────────────────

    @Test
    @DisplayName("non-owner + explicit MINOR draft version → 403 «Нет доступа к черновой версии шаблона» and nothing persisted")
    void create_nonOwnerExplicitDraftVersion_returns403AndNoRow() throws Exception {
        // given — B saves a draft version on top of the published one.
        int draftVersion = saveMinorDraft("черновик для не-владельца");
        assertThat(draftVersion).isGreaterThan(publishedVersionNumber);

        // when — A asks for the draft version explicitly.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Draft Version Doc " + suffix, null, draftVersion)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(DRAFT_VERSION_DENIED));

        // then — nothing was persisted.
        assertThat(documentRegistryRepository.existsByTemplateId(templateId)).isFalse();
        assertThat(countMongoDocsForTemplate(templateId)).isZero();
    }

    // ── 2. non-owner + explicit published version → 201, pinned to it ──────────

    @Test
    @DisplayName("non-owner + explicit published version → 201 pinned to that version")
    void create_nonOwnerExplicitPublishedVersion_returns201PinnedToIt() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Published Version Doc " + suffix, null, publishedVersionNumber)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.templateId").value(templateId.toString()))
                .andExpect(jsonPath("$.templateVersionNumber").value(publishedVersionNumber))
                .andExpect(jsonPath("$.templateSnapshotId").value(publishedSnapshotId.toString()));

        assertPinnedTo(publishedVersionNumber, publishedSnapshotId);
    }

    // ── 3. non-owner without version + newer draft → pinned to published ───────

    @Test
    @DisplayName("non-owner without version while a newer MINOR draft exists → 201 pinned to the latest published version, not the draft")
    void create_nonOwnerWithoutVersion_pinsToLatestPublishedNotToDraft() throws Exception {
        // given — a draft newer than the published version.
        int draftVersion = saveMinorDraft("черновик новее опубликованного");
        assertThat(draftVersion).isGreaterThan(publishedVersionNumber);

        // when — A creates a document without specifying a version.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "No Version Doc " + suffix, null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateVersionNumber").value(publishedVersionNumber))
                .andExpect(jsonPath("$.templateSnapshotId").value(publishedSnapshotId.toString()));

        // then — pinned to the published version, NOT to the newer draft.
        assertPinnedTo(publishedVersionNumber, publishedSnapshotId);
    }

    // ── 4. owner + explicit own draft version → 201 ───────────────────────────

    @Test
    @DisplayName("owner + explicit own draft version → 201 pinned to the draft")
    void create_ownerExplicitOwnDraftVersion_returns201() throws Exception {
        // given — B saves a draft version.
        int draftVersion = saveMinorDraft("собственный черновик владельца");
        UUID draftSnapshotId = versionOf(draftVersion).id();

        // when — B (the owner) instantiates the draft explicitly.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userBToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Owner Draft Doc " + suffix, null, draftVersion)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateVersionNumber").value(draftVersion))
                .andExpect(jsonPath("$.templateSnapshotId").value(draftSnapshotId.toString()));

        assertPinnedTo(draftVersion, draftSnapshotId);
    }

    // ── 5. owner without version → pins to the latest (draft) ─────────────────

    @Test
    @DisplayName("owner without version after saving a draft → 201 pinned to the latest version (the draft)")
    void create_ownerWithoutVersion_pinsToLatestDraft() throws Exception {
        // given — B saves a draft version (newer than the published one).
        int draftVersion = saveMinorDraft("черновик владельца без явной версии");
        assertThat(draftVersion).isGreaterThan(publishedVersionNumber);
        UUID draftSnapshotId = versionOf(draftVersion).id();

        // when — B creates a document without specifying a version.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userBToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Owner No Version Doc " + suffix, null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateVersionNumber").value(draftVersion))
                .andExpect(jsonPath("$.templateSnapshotId").value(draftSnapshotId.toString()));

        // then — the owner works with the newest snapshot of their own template.
        assertPinnedTo(draftVersion, draftSnapshotId);
    }

    // ── 6. legacy version without bump_type → counts as published ─────────────

    @Test
    @DisplayName("legacy template_version row without bump_type → non-owner without version → 201 pinned to that version, not to a newer draft")
    void create_nonOwnerLegacyVersionWithoutBumpType_returns201() throws Exception {
        // given — the PUBLISH row is turned into a legacy row: before migration V015 the
        // bump_type column did not exist, so legacy rows carry NULL there.
        makeLegacyVersionWithoutBumpType(publishedVersionNumber);
        assertThat(versionOf(publishedVersionNumber).bumpType()).isNull();

        // … and a newer draft is saved on top of it.
        int draftVersion = saveMinorDraft("черновик поверх легаси-версии");
        assertThat(draftVersion).isGreaterThan(publishedVersionNumber);

        // when — A creates a document without specifying a version.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Legacy Version Doc " + suffix, null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateVersionNumber").value(publishedVersionNumber))
                .andExpect(jsonPath("$.templateSnapshotId").value(publishedSnapshotId.toString()));

        // then — a NULL bump_type is treated as published, so the legacy version is used
        // (and the newer draft is not).
        assertPinnedTo(publishedVersionNumber, publishedSnapshotId);
    }

    @Test
    @DisplayName("legacy template_version row without bump_type → non-owner with explicit version → 201")
    void create_nonOwnerLegacyVersionExplicitly_returns201() throws Exception {
        // given — same legacy row as above.
        makeLegacyVersionWithoutBumpType(publishedVersionNumber);

        // when — A asks for that version explicitly.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Legacy Explicit Doc " + suffix, null, publishedVersionNumber)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateVersionNumber").value(publishedVersionNumber))
                .andExpect(jsonPath("$.templateSnapshotId").value(publishedSnapshotId.toString()));

        assertPinnedTo(publishedVersionNumber, publishedSnapshotId);
    }

    // ── 7. ROLLBACK of a PUBLISHED version → published by provenance ───────────

    @Test
    @DisplayName("ROLLBACK of a published version → non-owner without version → 201 pinned to the ROLLBACK version (published by provenance)")
    void create_nonOwnerWithoutVersion_pinsToRollbackOfPublishedVersion() throws Exception {
        // given — the owner rolls the template back to the published version; the rollback
        // creates a NEW version (bumpType = "ROLLBACK", rollback_from = published version).
        TemplateVersionDTO rollbackVersion = rollbackTo(publishedVersionNumber);
        assertThat(rollbackVersion.bumpType()).isEqualTo("ROLLBACK");
        assertThat(rollbackVersion.rollbackFrom()).isEqualTo(publishedVersionNumber);
        assertThat(rollbackVersion.versionNumber()).isGreaterThan(publishedVersionNumber);

        // when — A creates a document without specifying a version.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Rollback Published Doc " + suffix, null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateVersionNumber").value(rollbackVersion.versionNumber()))
                .andExpect(jsonPath("$.templateSnapshotId").value(rollbackVersion.id().toString()));

        // then — the rollback inherits "published" from its source, so it is the newest
        // published version and the document is pinned to it.
        assertPinnedTo(rollbackVersion.versionNumber(), rollbackVersion.id());
    }

    @Test
    @DisplayName("ROLLBACK of a published version → non-owner with explicit ROLLBACK version → 201")
    void create_nonOwnerExplicitRollbackOfPublishedVersion_returns201() throws Exception {
        // given — the owner rolls the template back to the published version.
        TemplateVersionDTO rollbackVersion = rollbackTo(publishedVersionNumber);

        // when — A asks for the rollback version explicitly.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Rollback Published Explicit Doc " + suffix, null,
                                rollbackVersion.versionNumber())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateVersionNumber").value(rollbackVersion.versionNumber()))
                .andExpect(jsonPath("$.templateSnapshotId").value(rollbackVersion.id().toString()));

        assertPinnedTo(rollbackVersion.versionNumber(), rollbackVersion.id());
    }

    // ── 8. ROLLBACK of a DRAFT version → stays a draft ────────────────────────

    @Test
    @DisplayName("ROLLBACK of a MINOR draft → non-owner with explicit ROLLBACK version → 403 «Нет доступа к черновой версии шаблона»")
    void create_nonOwnerExplicitRollbackOfDraftVersion_returns403() throws Exception {
        // given — the owner rolls the template back to a DRAFT version; the rollback inherits
        // the draft status of its source and stays invisible to non-owners.
        int draftVersion = saveMinorDraft("черновик-источник отката");
        TemplateVersionDTO rollbackVersion = rollbackTo(draftVersion);
        assertThat(rollbackVersion.bumpType()).isEqualTo("ROLLBACK");
        assertThat(rollbackVersion.rollbackFrom()).isEqualTo(draftVersion);

        // when — A asks for the rollback version explicitly.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Rollback Draft Explicit Doc " + suffix, null,
                                rollbackVersion.versionNumber())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(DRAFT_VERSION_DENIED));

        // then — nothing was persisted.
        assertThat(documentRegistryRepository.existsByTemplateId(templateId)).isFalse();
        assertThat(countMongoDocsForTemplate(templateId)).isZero();
    }

    @Test
    @DisplayName("ROLLBACK of a MINOR draft → non-owner without version → 201 pinned to the newest PUBLISH version, not to the rollback")
    void create_nonOwnerWithoutVersion_pinsToPublishNotToRollbackOfDraft() throws Exception {
        // given — a draft and a rollback of that draft.
        int draftVersion = saveMinorDraft("черновик-источник отката");
        TemplateVersionDTO rollbackVersion = rollbackTo(draftVersion);
        assertThat(rollbackVersion.versionNumber()).isGreaterThan(draftVersion);

        // when — A creates a document without specifying a version.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Rollback Draft No Version Doc " + suffix, null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateVersionNumber").value(publishedVersionNumber))
                .andExpect(jsonPath("$.templateSnapshotId").value(publishedSnapshotId.toString()));

        // then — neither the draft nor its rollback is published, so the newest PUBLISH
        // version wins.
        assertPinnedTo(publishedVersionNumber, publishedSnapshotId);
    }

    // ── 9. MAJOR draft → draft for non-owners ─────────────────────────────────

    @Test
    @DisplayName("non-owner + explicit MAJOR draft version → 403 «Нет доступа к черновой версии шаблона» and nothing persisted")
    void create_nonOwnerExplicitMajorDraftVersion_returns403AndNoRow() throws Exception {
        // given — B saves a MAJOR draft (a new major line) on top of the published version.
        int majorVersion = templateService.saveVersion(templateId, new SaveVersionRequest("MAJOR", "мажорный черновик"))
                .version()
                .versionNumber();
        assertThat(majorVersion).isGreaterThan(publishedVersionNumber);
        assertThat(versionOf(majorVersion).bumpType()).isEqualTo("MAJOR");

        // when — A asks for the MAJOR draft explicitly.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Major Draft Doc " + suffix, null, majorVersion)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(DRAFT_VERSION_DENIED));

        // then — nothing was persisted.
        assertThat(documentRegistryRepository.existsByTemplateId(templateId)).isFalse();
        assertThat(countMongoDocsForTemplate(templateId)).isZero();
    }

    // ── 10. unknown version number → 404 (owner and non-owner alike) ──────────

    @Test
    @DisplayName("non-owner + nonexistent version number → 404 «Снимок шаблона не найден» and nothing persisted")
    void create_nonOwnerNonexistentVersion_returns404AndNoRow() throws Exception {
        int missingVersion = publishedVersionNumber + 777;

        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Missing Version Doc " + suffix, null, missingVersion)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString(SNAPSHOT_NOT_FOUND)));

        // then — nothing was persisted.
        assertThat(documentRegistryRepository.existsByTemplateId(templateId)).isFalse();
        assertThat(countMongoDocsForTemplate(templateId)).isZero();
    }

    @Test
    @DisplayName("owner + nonexistent version number → 404 «Снимок шаблона не найден» and nothing persisted")
    void create_ownerNonexistentVersion_returns404AndNoRow() throws Exception {
        // The owner may use any of their own versions, but a version that does not exist
        // is still a 404 — not a silent fall back to the latest snapshot.
        int missingVersion = publishedVersionNumber + 777;

        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userBToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Missing Version Owner Doc " + suffix, null, missingVersion)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString(SNAPSHOT_NOT_FOUND)));

        // then — nothing was persisted.
        assertThat(documentRegistryRepository.existsByTemplateId(templateId)).isFalse();
        assertThat(countMongoDocsForTemplate(templateId)).isZero();
    }

    // ── 11. template without any published version → 403 for a non-owner ──────

    @Test
    @DisplayName("template with version rows but none published → non-owner create → 403 «Нет опубликованной версии шаблона» and nothing persisted")
    void create_nonOwnerTemplateWithoutPublishedVersion_returns403AndNoRow() throws Exception {
        // given — the template stays published for everybody, but none of its versions is
        // published: the PUBLISH row is turned into a draft and one more draft is saved.
        makeDraftVersion(publishedVersionNumber);
        int draftVersion = saveMinorDraft("черновик вместо публикации");
        assertThat(draftVersion).isGreaterThan(publishedVersionNumber);
        assertThat(templateService.listVersions(templateId).stream()
                .noneMatch(v -> "PUBLISH".equals(v.bumpType()))).isTrue();

        // when — A creates a document without specifying a version.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "No Published Version Doc " + suffix, null, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(NO_PUBLISHED_VERSION));

        // then — the live template is NOT used as a fallback (it may carry draft content
        // after a rollback), so nothing is persisted at all.
        assertThat(documentRegistryRepository.existsByTemplateId(templateId)).isFalse();
        assertThat(registryRows()).isEmpty();
        assertThat(countMongoDocsForTemplate(templateId)).isZero();
    }

    // ── 12. legacy template without any version rows → unpinned document ──────

    @Test
    @DisplayName("legacy template without any version rows → non-owner create → 201, document created unpinned with the live template's questionnaire")
    void create_nonOwnerLegacyTemplateWithoutVersionRows_createsUnpinnedDocumentFromLiveTemplate()
            throws Exception {
        // given — the template was published before versioning existed: it has no
        // template_version rows and no snapshots in MongoDB, but it is published.
        String questionnaireTitle = "Легаси-опросный лист " + suffix;
        String questionLabel = "Легаси-вопрос " + suffix;
        setLiveTemplateQuestionnaire(questionnaireTitle, questionLabel);
        deleteAllVersionRows();
        deleteAllMongoSnapshots();
        assertThat(versionRowCount()).isZero();
        assertThat(mongoSnapshotCount()).isZero();
        assertThat(templateService.getById(templateId).published()).isTrue();

        // when — A creates a document without specifying a version.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Legacy Unpinned Doc " + suffix, null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                // no versions at all → nothing to pin to
                .andExpect(jsonPath("$.templateVersionNumber").value(nullValue()))
                .andExpect(jsonPath("$.templateSnapshotId").value(nullValue()))
                // the questionnaire comes from the live template
                .andExpect(jsonPath("$.questionnaire.title").value(questionnaireTitle))
                .andExpect(jsonPath("$.questionnaire.pages[0].questions[0].label").value(questionLabel));

        // then — the document exists, but it is NOT pinned to any snapshot: it works with
        // the live template (the document body is generated from the current template).
        List<RegistryRow> rows = registryRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).templateVersionNumber()).isNull();
        assertThat(rows.get(0).templateSnapshotId()).isNull();
        assertThat(countMongoDocsForTemplate(templateId)).isEqualTo(1);
    }

    // ── 13. explicit version with a deleted MongoDB snapshot → 404 ───────────

    @Test
    @DisplayName("explicit version whose Mongo snapshot was deleted → non-owner create → 404 «Снимок версии шаблона не найден» and nothing persisted")
    void create_nonOwnerExplicitVersionWithDeletedSnapshot_returns404AndNoRow() throws Exception {
        // given — the version row is still there, but its snapshot was deleted from MongoDB.
        // No other snapshot of this template shares the version number, so the legacy
        // fallback (lookup by version number) cannot rescue the request either.
        deleteMongoSnapshot(publishedSnapshotId);
        assertThat(mongoSnapshotCount()).isZero();

        // when — A asks for that version explicitly.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Deleted Snapshot Doc " + suffix, null,
                                publishedVersionNumber)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString(VERSION_SNAPSHOT_NOT_FOUND)));

        // then — nothing was persisted (the document is NOT created unpinned).
        assertThat(documentRegistryRepository.existsByTemplateId(templateId)).isFalse();
        assertThat(registryRows()).isEmpty();
        assertThat(countMongoDocsForTemplate(templateId)).isZero();
    }

    @Test
    @DisplayName("explicit version whose Mongo snapshot was deleted → owner create → 404 «Снимок версии шаблона не найден» and nothing persisted")
    void create_ownerExplicitVersionWithDeletedSnapshot_returns404AndNoRow() throws Exception {
        // given — same as above: the version exists, its snapshot does not.
        deleteMongoSnapshot(publishedSnapshotId);
        assertThat(mongoSnapshotCount()).isZero();

        // when — B (the owner) asks for that version explicitly.
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userBToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(templateId, "Deleted Snapshot Owner Doc " + suffix, null,
                                publishedVersionNumber)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString(VERSION_SNAPSHOT_NOT_FOUND)));

        // then — nothing was persisted.
        assertThat(documentRegistryRepository.existsByTemplateId(templateId)).isFalse();
        assertThat(registryRows()).isEmpty();
        assertThat(countMongoDocsForTemplate(templateId)).isZero();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** Saves a MINOR draft version (bumpType = "MINOR") and returns its version number. */
    private int saveMinorDraft(String comment) {
        return templateService.saveVersion(templateId, new SaveVersionRequest("MINOR", comment))
                .version()
                .versionNumber();
    }

    /**
     * Rolls the template back to the given version (as the template owner) and returns the
     * metadata of the newly created ROLLBACK version.
     */
    private TemplateVersionDTO rollbackTo(int targetVersionNumber) {
        return templateService.rollback(templateId, targetVersionNumber).version();
    }

    /** Metadata of a single version of the test template. */
    private TemplateVersionDTO versionOf(int versionNumber) {
        return templateService.listVersions(templateId).stream()
                .filter(v -> v.versionNumber() == versionNumber)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Version not found: " + versionNumber));
    }

    /**
     * Rewrites {@code bump_type} to NULL for the given version — the shape of a row created
     * before migration V015 introduced the column. The snapshot in MongoDB is untouched, so
     * the version stays instantiable and observable.
     */
    private void makeLegacyVersionWithoutBumpType(int versionNumber) {
        int updated = jdbcTemplate.update(
                "UPDATE template_version SET bump_type = NULL WHERE template_id = ? AND version_number = ?",
                templateId, versionNumber);
        assertThat(updated).isEqualTo(1);
    }

    /**
     * Rewrites {@code bump_type} of the given version to {@code MINOR}, i.e. turns a
     * published version into an ordinary draft.
     */
    private void makeDraftVersion(int versionNumber) {
        int updated = jdbcTemplate.update(
                "UPDATE template_version SET bump_type = 'MINOR' WHERE template_id = ? AND version_number = ?",
                templateId, versionNumber);
        assertThat(updated).isEqualTo(1);
    }

    /**
     * Emulates a template published before versioning existed: all {@code template_version}
     * rows of the template are removed (the template itself stays published).
     */
    private void deleteAllVersionRows() {
        int deleted = jdbcTemplate.update("DELETE FROM template_version WHERE template_id = ?", templateId);
        assertThat(deleted).isPositive();
    }

    /** Number of {@code template_version} rows of the test template. */
    private int versionRowCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM template_version WHERE template_id = ?", Integer.class, templateId);
        return count == null ? 0 : count;
    }

    /** Deletes the MongoDB snapshot with the given id. */
    private void deleteMongoSnapshot(UUID snapshotId) {
        var deleted = mongoTemplate.remove(Query.query(Criteria.where("_id").is(snapshotId)), "template_snapshots");
        assertThat(deleted.getDeletedCount()).isEqualTo(1);
    }

    /** Deletes all MongoDB snapshots of the test template. */
    private void deleteAllMongoSnapshots() {
        mongoTemplate.remove(Query.query(Criteria.where("template_id").is(templateId)), "template_snapshots");
    }

    /** Number of MongoDB snapshots of the test template. */
    private long mongoSnapshotCount() {
        return mongoTemplate.count(
                Query.query(Criteria.where("template_id").is(templateId)), "template_snapshots");
    }

    /**
     * Writes a questionnaire (and its parameter) into the <em>live</em> template document in
     * MongoDB — the content a legacy template (published before versioning) would carry.
     *
     * <p>The template is already published at this point, so the service API refuses to
     * update it; the document is therefore written directly.</p>
     */
    private void setLiveTemplateQuestionnaire(String questionnaireTitle, String questionLabel) {
        Template live = mongoTemplate.findById(templateId, Template.class);
        assertThat(live).isNotNull();

        live.setParameters(List.of(Parameter.builder()
                .code("legacy_param")
                .label("Легаси-параметр " + suffix)
                .type(ParameterType.TEXT)
                .required(false)
                .build()));
        live.setQuestionnaire(Questionnaire.builder()
                .title(questionnaireTitle)
                .pages(List.of(QuestionnairePage.builder()
                        .title("Страница 1")
                        .questions(List.of(Question.builder()
                                .id("q1")
                                .parameterCode("legacy_param")
                                .label(questionLabel)
                                .order(1)
                                .build()))
                        .build()))
                .build());

        mongoTemplate.save(live);
    }

    /** One row of {@code document_registry} of the test template. */
    private record RegistryRow(UUID id, Integer templateVersionNumber, UUID templateSnapshotId) {}

    /**
     * Rows of {@code document_registry} of the test template — a scoped query instead of a
     * full-table scan.
     */
    private List<RegistryRow> registryRows() {
        return jdbcTemplate.query(
                """
                SELECT id, template_version_number, template_snapshot_id
                FROM document_registry
                WHERE template_id = ?
                """,
                (rs, rowNum) -> new RegistryRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("template_version_number", Integer.class),
                        rs.getObject("template_snapshot_id", UUID.class)),
                templateId);
    }

    /** Asserts that exactly one document exists and that it is pinned to the given version. */
    private void assertPinnedTo(int versionNumber, UUID snapshotId) {
        assertThat(documentRegistryRepository.existsByTemplateId(templateId)).isTrue();

        List<RegistryRow> rows = registryRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).templateVersionNumber()).isEqualTo(versionNumber);
        assertThat(rows.get(0).templateSnapshotId()).isEqualTo(snapshotId);

        assertThat(countMongoDocsForTemplate(templateId)).isEqualTo(1);
    }

    /** Number of MongoDB documents of the given template — scoped query, no full-table scan. */
    private long countMongoDocsForTemplate(UUID templateId) {
        return mongoTemplate.count(Query.query(Criteria.where("template_id").is(templateId)), Document.class);
    }

    private UUID createUser(String login, String displayName) {
        User user = new User();
        user.setLogin(login);
        user.setPasswordHash("hash");
        user.setDisplayName(displayName);
        return userRepository.save(user).getId();
    }

    private static String createBody(UUID templateId, String name, UUID folderId, Integer versionNumber) {
        return String.format(
                "{\"templateId\":\"%s\",\"name\":\"%s\",\"folderId\":%s,\"templateVersionNumber\":%s}",
                templateId,
                name,
                folderId == null ? "null" : "\"" + folderId + "\"",
                versionNumber == null ? "null" : versionNumber);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * Cleanup of the shared dev database: documents first (FK to the template), then the
     * template (FK cascade to {@code template_version}), then the users. Every statement is
     * scoped to the ids created by this test; failures are <em>not</em> swallowed — a
     * broken cleanup must fail the test, otherwise leftovers silently distort the next runs.
     */
    @AfterEach
    void cleanUp() {
        if (templateId != null) {
            mongoTemplate.remove(Query.query(Criteria.where("template_id").is(templateId)), "documents");
            jdbcTemplate.update("DELETE FROM document_registry WHERE template_id = ?", templateId);

            mongoTemplate.remove(Query.query(Criteria.where("template_id").is(templateId)), "template_snapshots");
            mongoTemplate.remove(Query.query(Criteria.where("_id").is(templateId)), "templates");
            jdbcTemplate.update("DELETE FROM template_registry WHERE id = ?", templateId);
        }
        for (UUID userId : List.of(userAId, userBId)) {
            if (userId == null) continue;
            jdbcTemplate.update("DELETE FROM audit_log WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
    }
}
