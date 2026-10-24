package com.docforge.template;

import com.docforge.AbstractIT;
import com.docforge.AuditTestSupport;
import com.docforge.TestAuth;
import com.docforge.common.exception.TemplatePublishedException;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.dto.UpdateTemplateRequest;
import com.docforge.template.model.TemplateSnapshot;
import com.docforge.template.model.TemplateVersion;
import com.docforge.user.User;
import com.docforge.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test: publish() creates TemplateVersion (PostgreSQL) + TemplateSnapshot (MongoDB).
 * Covers BUG-001 fix: TemplateSnapshot.createdAt = Instant (was OffsetDateTime — caused codec error).
 *
 * <p>BUG-068: since the fix the template module resolves the caller from the security
 * context and only the owner of a template (or an administrator) may change it. The test
 * therefore acts as a real owner: a row in {@code users} is created, the template is
 * created with that id as {@code owner_id} and the security context of the thread carries
 * the login of that user.</p>
 */
class TemplateServiceImplIT extends AbstractIT {

    @Autowired
    private TemplateService templateService;

    @Autowired
    private TemplateVersionRepository versionRepository;

    @Autowired
    private TemplateSnapshotRepository snapshotRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Владелец шаблонов теста. */
    private UUID ownerId;
    private String ownerLogin;

    /** Шаблоны, созданные тестом — чистятся в {@code @AfterEach}. */
    private final List<UUID> templateIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ownerLogin = "tpl-svc-owner-" + suffix;
        User owner = new User();
        owner.setLogin(ownerLogin);
        owner.setPasswordHash("hash");
        owner.setDisplayName("Template Service Owner");
        ownerId = userRepository.save(owner).getId();

        TestAuth.authenticateAs(ownerLogin);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
        for (UUID templateId : templateIds) {
            if (templateId == null) continue;
            // Аудит пишется асинхронно (@Async) — ждём, пока он догонит тест,
            // иначе удаление пользователя нарушит FK audit_log → users.
            AuditTestSupport.awaitQuiescence(jdbcTemplate,
                    "SELECT COUNT(*) FROM audit_log WHERE user_id = ?", ownerId);
            mongoTemplate.remove(Query.query(Criteria.where("template_id").is(templateId)), "template_snapshots");
            mongoTemplate.remove(Query.query(Criteria.where("_id").is(templateId)), "templates");
            jdbcTemplate.update("DELETE FROM template_version WHERE template_id = ?", templateId);
            jdbcTemplate.update("DELETE FROM template_registry WHERE id = ?", templateId);
            jdbcTemplate.update("DELETE FROM audit_log WHERE entity_id = ?", templateId);
        }
        // Аудит асинхронный (@Async) — удаляем пользователя вместе с его строками
        // аудита детерминированно (retry при поздней строке аудита).
        AuditTestSupport.deleteUserWithAuditRows(jdbcTemplate, ownerId);
        templateIds.clear();
    }

    /** Создаёт шаблон от имени владельца и запоминает его для очистки. */
    private TemplateResponse createTemplate(String name) {
        TemplateResponse created = templateService.create(
                ownerId,
                new CreateTemplateRequest(name, null, null, null)
        );
        templateIds.add(created.id());
        return created;
    }

    @Test
    @DisplayName("publish() creates TemplateVersion in PostgreSQL and TemplateSnapshot in MongoDB")
    void publish_createsVersionAndSnapshot() {
        // Arrange — create template owned by the caller (ownerId = the caller's id)
        TemplateResponse created = createTemplate("Test Template");
        var templateId = created.id();

        // Act
        TemplateResponse published = templateService.publish(templateId);

        // Assert — PostgreSQL: template_version row exists
        List<TemplateVersion> versions =
                versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId);
        assertThat(versions).hasSize(1);
        TemplateVersion version = versions.get(0);
        // BUG-054: publish() consumes the semantic counter — 1.0 → 1.1
        assertThat(version.getVersionNumber()).isEqualTo(10_001);
        assertThat(version.getMajorVersion()).isEqualTo(1);
        assertThat(version.getMinorVersion()).isEqualTo(1);
        assertThat(version.getCreatedAt()).isNotNull();
        // BUG-068: вызывающий аутентифицирован — created_by содержит его id
        assertThat(version.getCreatedBy()).isEqualTo(ownerId);

        // Assert — MongoDB: snapshot exists
        TemplateSnapshot snapshot =
                snapshotRepository.findFirstByTemplateIdAndVersionNumberOrderByCreatedAtDesc(templateId, 10_001)
                        .orElse(null);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getTemplateId()).isEqualTo(templateId);
        assertThat(snapshot.getVersionNumber()).isEqualTo(10_001);
        // BUG-001 fix: createdAt must be Instant (not null) — proves codec issue is resolved
        assertThat(snapshot.getCreatedAt()).isNotNull();

        // Assert — published version counter
        assertThat(published.version()).isEqualTo(10_001);
        assertThat(published.majorVersion()).isEqualTo(1);
        assertThat(published.minorVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("update() does not increment version counter (only publish() should)")
    void update_doesNotIncrementVersion() {
        TemplateResponse created = createTemplate("Draft Template");
        var templateId = created.id();
        int versionBefore = created.version();

        com.docforge.template.dto.UpdateTemplateRequest updateRequest =
                new com.docforge.template.dto.UpdateTemplateRequest(
                        "Draft Template Updated", null, null,
                        null, null, null, null, null, null, null
                );
        TemplateResponse updated = templateService.update(templateId, updateRequest);

        assertThat(updated.version()).isEqualTo(versionBefore);
        assertThat(versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId)).isEmpty();
    }

    @Test
    @DisplayName("update() throws TemplatePublishedException when template is PUBLISHED")
    void update_publishedTemplate_throwsException() {
        TemplateResponse created = createTemplate("Published Template");
        var templateId = created.id();
        templateService.publish(templateId);

        UpdateTemplateRequest updateRequest = new UpdateTemplateRequest(
                "New Name", null, null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> templateService.update(templateId, updateRequest))
                .isInstanceOf(TemplatePublishedException.class)
                .hasMessageContaining(templateId.toString());
    }

    @Test
    @DisplayName("update() succeeds for DRAFT template (regression)")
    void update_draftTemplate_succeeds() {
        TemplateResponse created = createTemplate("Draft Template Regression");
        var templateId = created.id();

        UpdateTemplateRequest updateRequest = new UpdateTemplateRequest(
                "Draft Template Regression Updated", null, null,
                null, null, null, null, null, null, null
        );

        TemplateResponse updated = templateService.update(templateId, updateRequest);
        assertThat(updated.name()).isEqualTo("Draft Template Regression Updated");
    }

    /**
     * BUG-053 regression guard (integration, real MongoDB).
     * <p>
     * Previously {@code getSnapshot()} used {@code findByTemplateIdAndVersionNumber} which returns
     * {@code Optional} and throws {@code IncorrectResultSizeDataAccessException} as soon as MongoDB
     * holds more than one {@code template_snapshots} document for the same
     * {@code (template_id, version_number)} pair. It now uses
     * {@code findFirstByTemplateIdAndVersionNumberOrderByCreatedAtDesc}, so the newest snapshot
     * must be returned without throwing.
     * <p>
     * A mock-based unit test cannot reproduce this failure mode — only a real Mongo query can.
     */
    @Test
    @DisplayName("BUG-053: getSnapshot() returns newest snapshot when duplicates exist for same version (no IncorrectResultSizeDataAccessException)")
    void getSnapshot_duplicateSnapshotsForSameVersion_returnsNewestWithoutThrowing() {
        // Arrange — registry must exist for findRegistryOrThrow(); no TemplateVersion row needed
        TemplateResponse created = createTemplate("Duplicate Snapshot Template");
        UUID templateId = created.id();
        int versionNumber = 1;

        UUID olderId = UUID.randomUUID();
        UUID newerId = UUID.randomUUID();
        Instant olderCreatedAt = Instant.parse("2026-01-01T10:00:00Z");
        Instant newerCreatedAt = Instant.parse("2026-01-01T12:00:00Z");

        TemplateSnapshot older = TemplateSnapshot.builder()
                .id(olderId)
                .templateId(templateId)
                .versionNumber(versionNumber)
                .majorVersion(1)
                .minorVersion(0)
                .createdAt(olderCreatedAt)
                .build();
        TemplateSnapshot newer = TemplateSnapshot.builder()
                .id(newerId)
                .templateId(templateId)
                .versionNumber(versionNumber)
                .majorVersion(1)
                .minorVersion(0)
                .createdAt(newerCreatedAt)
                .build();

        try {
            // older first, newer second — MongoDB order must not matter
            snapshotRepository.save(older);
            snapshotRepository.save(newer);

            // Prove the duplicate state really exists in MongoDB (no unique index on the pair),
            // otherwise the regression below would pass trivially.
            long persisted = mongoTemplate.count(
                    Query.query(Criteria.where("template_id").is(templateId)
                            .and("version_number").is(versionNumber)),
                    TemplateSnapshot.class);
            assertThat(persisted).isEqualTo(2);

            // Act + Assert — no IncorrectResultSizeDataAccessException
            assertThatCode(() -> templateService.getSnapshot(templateId, versionNumber))
                    .doesNotThrowAnyException();

            TemplateSnapshot result = templateService.getSnapshot(templateId, versionNumber);

            assertThat(result.getId()).isEqualTo(newerId);
            assertThat(result.getCreatedAt()).isEqualTo(newerCreatedAt);
            assertThat(result.getTemplateId()).isEqualTo(templateId);
            assertThat(result.getVersionNumber()).isEqualTo(versionNumber);
        } finally {
            // isolation — this class shares the dev Mongo instance
            snapshotRepository.deleteById(olderId);
            snapshotRepository.deleteById(newerId);
        }
    }
}
