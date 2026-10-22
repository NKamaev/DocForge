package com.docforge.template;

import com.docforge.AbstractIT;
import com.docforge.common.exception.TemplatePublishedException;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.dto.UpdateTemplateRequest;
import com.docforge.template.model.TemplateSnapshot;
import com.docforge.template.model.TemplateVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test: publish() creates TemplateVersion (PostgreSQL) + TemplateSnapshot (MongoDB).
 * Covers BUG-001 fix: TemplateSnapshot.createdAt = Instant (was OffsetDateTime — caused codec error).
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

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("publish() creates TemplateVersion in PostgreSQL and TemplateSnapshot in MongoDB")
    void publish_createsVersionAndSnapshot() {
        // Arrange — create template (ownerId = null, no FK violation)
        TemplateResponse created = templateService.create(
                null,
                new CreateTemplateRequest("Test Template", "desc", "test", null)
        );
        var templateId = created.id();

        // Act
        TemplateResponse published = templateService.publish(templateId);

        // Assert — PostgreSQL: template_version row exists
        List<TemplateVersion> versions =
                versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId);
        assertThat(versions).hasSize(1);
        TemplateVersion version = versions.get(0);
        assertThat(version.getVersionNumber()).isEqualTo(1);
        assertThat(version.getCreatedAt()).isNotNull();

        // Assert — MongoDB: snapshot exists
        TemplateSnapshot snapshot =
                snapshotRepository.findFirstByTemplateIdAndVersionNumberOrderByCreatedAtDesc(templateId, 1)
                        .orElse(null);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getTemplateId()).isEqualTo(templateId);
        assertThat(snapshot.getVersionNumber()).isEqualTo(1);
        // BUG-001 fix: createdAt must be Instant (not null) — proves codec issue is resolved
        assertThat(snapshot.getCreatedAt()).isNotNull();

        // Assert — published version counter
        assertThat(published.version()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("update() does not increment version counter (only publish() should)")
    void update_doesNotIncrementVersion() {
        TemplateResponse created = templateService.create(
                null,
                new CreateTemplateRequest("Draft Template", null, null, null)
        );
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
    @WithMockUser(username = "testuser")
    @DisplayName("update() throws TemplatePublishedException when template is PUBLISHED")
    void update_publishedTemplate_throwsException() {
        TemplateResponse created = templateService.create(
                null,
                new CreateTemplateRequest("Published Template", "desc", "test", null)
        );
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
    @WithMockUser(username = "testuser")
    @DisplayName("update() succeeds for DRAFT template (regression)")
    void update_draftTemplate_succeeds() {
        TemplateResponse created = templateService.create(
                null,
                new CreateTemplateRequest("Draft Template Regression", null, null, null)
        );
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
    @WithMockUser(username = "testuser")
    @DisplayName("BUG-053: getSnapshot() returns newest snapshot when duplicates exist for same version (no IncorrectResultSizeDataAccessException)")
    void getSnapshot_duplicateSnapshotsForSameVersion_returnsNewestWithoutThrowing() {
        // Arrange — registry must exist for findRegistryOrThrow(); no TemplateVersion row needed
        TemplateResponse created = templateService.create(
                null,
                new CreateTemplateRequest("Duplicate Snapshot Template", "BUG-053", "test", null)
        );
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
