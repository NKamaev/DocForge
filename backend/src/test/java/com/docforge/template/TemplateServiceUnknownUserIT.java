package com.docforge.template;

import com.docforge.AbstractIT;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.SaveVersionRequest;
import com.docforge.template.dto.SaveVersionResponse;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.model.TemplateVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * BUG-061 regression: транзакционное поведение сервиса шаблонов при логине,
 * которого нет в таблице {@code users}.
 * <p>
 * Раньше {@code TemplateServiceImpl.currentUserId()} вызывал бросающий
 * {@code userService.getCurrentUser(login)} внутри {@code @Transactional}-метода.
 * Вложенный вызов присоединялся к транзакции вызывающего метода, бросок
 * {@code ResourceNotFoundException} помечал общую транзакцию как rollback-only, и
 * на коммите бизнес-метод падал с {@code UnexpectedRollbackException} — изменения
 * терялись, хотя исключение было «проглочено».
 * <p>
 * Ожидаемое поведение после фикса: неизвестный логин — это нормальная ситуация,
 * {@code created_by} просто остаётся {@code null}, а изменения коммитятся.
 */
class TemplateServiceUnknownUserIT extends AbstractIT {

    /** Логин, которого гарантированно нет в таблице {@code users}. */
    private static final String UNKNOWN_LOGIN = "bug061-unknown-template-user";

    @Autowired
    private TemplateService templateService;

    @Autowired
    private TemplateVersionRepository versionRepository;

    @Autowired
    private TemplateRegistryRepository registryRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private TemplateSnapshotRepository snapshotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> templateIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Предусловие — обычный SQL: он работает и на старом, и на новом коде
        // (userService.findUser() появился только вместе с фиксом BUG-061).
        assertThat(countUsersByLogin(UNKNOWN_LOGIN))
                .as("login %s must not exist in table users", UNKNOWN_LOGIN)
                .isZero();
    }

    @AfterEach
    void tearDown() {
        for (UUID templateId : templateIds) {
            for (TemplateVersion version :
                    versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId)) {
                snapshotRepository.deleteById(version.getId());
            }
            jdbcTemplate.update("DELETE FROM template_version WHERE template_id = ?", templateId);
            jdbcTemplate.update("DELETE FROM template_registry WHERE id = ?", templateId);
            jdbcTemplate.update("DELETE FROM audit_log WHERE entity_id = ?", templateId);
            templateRepository.deleteById(templateId);
        }
        templateIds.clear();
    }

    @Test
    @WithMockUser(username = UNKNOWN_LOGIN)
    @DisplayName("BUG-061: publish() commits the version row when the security-context login is unknown")
    void publish_unknownSecurityLogin_commitsVersionWithoutRollbackOnly() {
        // Arrange
        UUID templateId = createTemplate("BUG-061 Publish Template");

        // Act — раньше падало с UnexpectedRollbackException на коммите транзакции
        assertThatCode(() -> templateService.publish(templateId))
                .as("BUG-061: publish() must not fail with UnexpectedRollbackException")
                .doesNotThrowAnyException();

        // Assert — версия закоммичена (а не откатилась)
        List<TemplateVersion> versions =
                versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId);
        assertThat(versions).hasSize(1);
        TemplateVersion version = versions.get(0);
        assertThat(version.getTemplateId()).isEqualTo(templateId);
        assertThat(version.getBumpType()).isEqualTo("PUBLISH");
        // created_by остаётся null — «неизвестный пользователь» это не ошибка
        assertThat(version.getCreatedBy()).isNull();

        // Assert — реестр шаблона тоже обновлён и закоммичен
        var registry = registryRepository.findById(templateId).orElseThrow();
        assertThat(registry.isPublished()).isTrue();
        assertThat(registry.getVersion()).isEqualTo(version.getVersionNumber());
    }

    @Test
    @WithMockUser(username = UNKNOWN_LOGIN)
    @DisplayName("BUG-061: saveVersion() commits the version row when the security-context login is unknown")
    void saveVersion_unknownSecurityLogin_commitsVersionWithoutRollbackOnly() {
        // Arrange
        UUID templateId = createTemplate("BUG-061 SaveVersion Template");

        // Act
        assertThatCode(() -> templateService.saveVersion(
                templateId, new SaveVersionRequest("MINOR", "BUG-061")))
                .as("BUG-061: saveVersion() must not fail with UnexpectedRollbackException")
                .doesNotThrowAnyException();

        // Assert
        List<TemplateVersion> versions =
                versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId);
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getBumpType()).isEqualTo("MINOR");
        assertThat(versions.get(0).getCreatedBy()).isNull();
    }

    @Test
    @WithMockUser(username = UNKNOWN_LOGIN)
    @DisplayName("BUG-061: publish() still writes the audit entry, but with a null user_id")
    void publish_unknownSecurityLogin_writesAuditEntryWithNullUserId() {
        // Arrange
        UUID templateId = createTemplate("BUG-061 Audit Template");

        // Act
        templateService.publish(templateId);

        // Assert — аудит пишется асинхронно, поэтому ждём запись
        assertThat(awaitAuditEntry("TEMPLATE", templateId, "PUBLISH"))
                .as("audit entry PUBLISH for template %s must be written", templateId)
                .isTrue();
        assertThat(countAuditEntriesWithNullUser("TEMPLATE", templateId, "PUBLISH"))
                .as("audit entry must have user_id = null for an unknown login")
                .isEqualTo(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Создаёт шаблон и запоминает его для очистки в {@code @AfterEach}. */
    private UUID createTemplate(String name) {
        TemplateResponse created = templateService.create(
                null, new CreateTemplateRequest(name, null, null, null));
        templateIds.add(created.id());
        return created.id();
    }

    private long countUsersByLogin(String login) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE login = ?", Long.class, login);
        return count == null ? 0L : count;
    }

    /** Ждёт появления записи в {@code audit_log} (аудит пишется асинхронно). */
    private boolean awaitAuditEntry(String entityType, UUID entityId, String action) {
        long deadline = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < deadline) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_log WHERE entity_type = ? AND entity_id = ? AND action = ?",
                    Long.class, entityType, entityId, action);
            if (count != null && count > 0) {
                return true;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private Long countAuditEntriesWithNullUser(String entityType, UUID entityId, String action) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log "
                        + "WHERE entity_type = ? AND entity_id = ? AND action = ? AND user_id IS NULL",
                Long.class, entityType, entityId, action);
    }
}
