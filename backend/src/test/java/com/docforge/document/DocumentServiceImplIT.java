package com.docforge.document;

import com.docforge.AbstractIT;
import com.docforge.document.dto.DocumentResponse;
import com.docforge.template.TemplateService;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.user.User;
import com.docforge.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: DocumentService.create() stores templateSnapshotId and templateVersionNumber.
 * Covers BUG-001 fix: documents must reference the published snapshot, not the live template.
 */
class DocumentServiceImplIT extends AbstractIT {

    @Autowired
    private TemplateService templateService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("create() sets templateSnapshotId and templateVersionNumber from published snapshot")
    void create_setsSnapshotReference() {
        // Arrange — create and publish template
        TemplateResponse template = templateService.create(
                null,
                new CreateTemplateRequest("Snapshot Test Template", null, null, null)
        );
        templateService.publish(template.id());

        // Act — create document
        DocumentResponse doc = documentService.create(template.id(), "Test Document", null, null, null);

        // Assert — BUG-001: snapshotId and versionNumber must be populated
        assertThat(doc.templateSnapshotId()).isNotNull();
        // BUG-054: first publish() produces semantic version 1.1 (10001)
        assertThat(doc.templateVersionNumber()).isEqualTo(10_001);
        assertThat(doc.templateId()).isEqualTo(template.id());
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("create() on unpublished template sets null snapshotId (no snapshot exists yet)")
    void create_withoutPublish_hasNullSnapshot() {
        // Arrange — create template owned by the caller (BUG-052 guard: unpublished templates are
        // only usable by their owner) but do NOT publish
        UUID ownerId = createOwner();
        TemplateResponse template = templateService.create(
                ownerId,
                new CreateTemplateRequest("Unpublished Template", null, null, null)
        );

        // Act
        DocumentResponse doc = documentService.create(template.id(), "Doc Without Snapshot", ownerId, null, null);

        // Assert — no snapshot → both fields are null
        assertThat(doc.templateSnapshotId()).isNull();
        assertThat(doc.templateVersionNumber()).isNull();
    }

    private UUID createOwner() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User owner = new User();
        owner.setLogin("doc-snapshot-owner-" + suffix);
        owner.setPasswordHash("hash");
        owner.setDisplayName("DocSnapshotOwner");
        return userRepository.save(owner).getId();
    }
}
