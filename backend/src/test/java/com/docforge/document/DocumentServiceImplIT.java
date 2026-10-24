package com.docforge.document;

import com.docforge.AbstractIT;
import com.docforge.AuditTestSupport;
import com.docforge.TestAuth;
import com.docforge.document.dto.DocumentResponse;
import com.docforge.template.TemplateService;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.TemplateResponse;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: DocumentService.create() stores templateSnapshotId and templateVersionNumber.
 * Covers BUG-001 fix: documents must reference the published snapshot, not the live template.
 *
 * <p>BUG-068: публиковать шаблон может только его владелец (или админ) — тест работает
 * от имени реального владельца (строка в {@code users} + контекст безопасности).</p>
 */
class DocumentServiceImplIT extends AbstractIT {

    @Autowired
    private TemplateService templateService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

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
        ownerLogin = "doc-snapshot-owner-" + suffix;
        User owner = new User();
        owner.setLogin(ownerLogin);
        owner.setPasswordHash("hash");
        owner.setDisplayName("DocSnapshotOwner");
        ownerId = userRepository.save(owner).getId();

        TestAuth.authenticateAs(ownerLogin);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
        for (UUID templateId : templateIds) {
            if (templateId == null) continue;
            AuditTestSupport.awaitQuiescence(jdbcTemplate,
                    "SELECT COUNT(*) FROM audit_log WHERE user_id = ?", ownerId);
            mongoTemplate.remove(Query.query(Criteria.where("template_id").is(templateId)), "documents");
            mongoTemplate.remove(Query.query(Criteria.where("template_id").is(templateId)), "template_snapshots");
            mongoTemplate.remove(Query.query(Criteria.where("_id").is(templateId)), "templates");
            jdbcTemplate.update("DELETE FROM document_registry WHERE template_id = ?", templateId);
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
                new CreateTemplateRequest(name, null, null, null));
        templateIds.add(created.id());
        return created;
    }

    @Test
    @DisplayName("create() sets templateSnapshotId and templateVersionNumber from published snapshot")
    void create_setsSnapshotReference() {
        // Arrange — create and publish template (as its owner)
        TemplateResponse template = createTemplate("Snapshot Test Template");
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
    @DisplayName("create() on unpublished template sets null snapshotId (no snapshot exists yet)")
    void create_withoutPublish_hasNullSnapshot() {
        // Arrange — create template owned by the caller (BUG-052 guard: unpublished templates are
        // only usable by their owner) but do NOT publish
        TemplateResponse template = createTemplate("Unpublished Template");

        // Act
        DocumentResponse doc = documentService.create(template.id(), "Doc Without Snapshot", ownerId, null, null);

        // Assert — no snapshot → both fields are null
        assertThat(doc.templateSnapshotId()).isNull();
        assertThat(doc.templateVersionNumber()).isNull();
    }
}
