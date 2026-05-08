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
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
                snapshotRepository.findByTemplateIdAndVersionNumber(templateId, 1)
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
}
