package com.docforge.template;

import com.docforge.AbstractIT;
import com.docforge.TestAuth;
import com.docforge.document.DocumentService;
import com.docforge.document.dto.DocumentResponse;
import com.docforge.document.dto.DocumentRevisionDTO;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.SaveVersionRequest;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.model.TemplateVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * BUG-068 (изменение правил прав на запись) добавило проверку
 * {@code TemplateServiceImpl.assertCanModify}: изменять шаблон может только владелец
 * или администратор, а неизвестный вызывающий (логин, которого нет в {@code users})
 * не является ни тем, ни другим — проверка закрыта по умолчанию (fail closed).
 * <p>
 * <b>Выбор при адаптации теста.</b> Вариант «оставить вызовы под неизвестным логином,
 * но в контексте администратора» невозможен: {@code assertCanModify} сначала
 * разрешает вызывающего через {@code UserService.findUser(login)} и для неизвестного
 * логина бросает {@code AccessDeniedException} до того, как проверяется роль
 * {@code ROLE_ADMIN}. Поэтому тест перестроен: неизвестный логин проверяется как
 * <em>отказ</em> (403 «Нет прав на изменение шаблона»), а контракт BUG-061
 * «неизвестный логин — это не ошибка, операция не должна падать с
 * {@code UnexpectedRollbackException}» сохраняется на незащищённых путях
 * ({@code create()} пишет аудит с {@code user_id = null}) и в самом отказе:
 * отказ происходит <em>до</em> любых записей, транзакция откатывается целиком и
 * бизнес-метод падает именно с {@code AccessDeniedException}, а не с
 * {@code UnexpectedRollbackException}.
 *
 * <p><b>Регрессия BUG-061 на незащищённом пути.</b> Ключевой инвариант BUG-061 —
 * «неизвестный логин внутри транзакции не помечает транзакцию как
 * rollback-only» — проверяется на пути, который <em>не</em> закрыт guard'ом
 * BUG-068: модуль {@code document} не проверяет права на запись. Под неизвестным
 * логином {@code documentService.create(...)} и {@code documentService.saveRevision(...)}
 * обязаны закоммититься, а поля атрибуции ({@code owner_id},
 * {@code document_revision.saved_by}, {@code audit_log.user_id}) — остаться
 * {@code null}. См. {@link #documentWrite_unknownSecurityLogin_commitsWithNullAttribution()}.</p>
 */
class TemplateServiceUnknownUserIT extends AbstractIT {

    /** Логин, которого гарантированно нет в таблице {@code users}. */
    private static final String UNKNOWN_LOGIN = "bug061-unknown-template-user";

    /** Отказ по правам (BUG-068). */
    private static final String MODIFY_DENIED = "Нет прав на изменение шаблона";

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

    @Autowired
    private DocumentService documentService;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final List<UUID> templateIds = new ArrayList<>();

    /** Документы, созданные тестом — для очистки. */
    private final List<UUID> documentIds = new ArrayList<>();

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
        for (UUID documentId : documentIds) {
            mongoTemplate.remove(Query.query(Criteria.where("document_id").is(documentId)), "document_revisions");
            mongoTemplate.remove(Query.query(Criteria.where("_id").is(documentId)), "documents");
            jdbcTemplate.update("DELETE FROM document_registry WHERE id = ?", documentId);
            jdbcTemplate.update("DELETE FROM audit_log WHERE entity_id = ?", documentId);
        }
        documentIds.clear();
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
    @DisplayName("BUG-068: publish() is refused for an unknown login and nothing is committed")
    void publish_unknownSecurityLogin_isRefusedAndNothingCommitted() {
        // Arrange
        UUID templateId = createTemplate("BUG-061 Publish Template");

        // Act — неизвестный логин не владелец и не админ → 403
        assertThatThrownBy(() -> templateService.publish(templateId))
                .as("BUG-068: unknown login must not be allowed to publish")
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(MODIFY_DENIED)
                // BUG-061: главное — бизнес-метод не должен падать с
                // UnexpectedRollbackException (отказ должен быть атомарным).
                .isNotInstanceOf(org.springframework.transaction.UnexpectedRollbackException.class);

        // Assert — версия НЕ создана (отказ до записи, транзакция откатилась)
        List<TemplateVersion> versions =
                versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId);
        assertThat(versions).isEmpty();

        // Assert — реестр шаблона не изменился
        var registry = registryRepository.findById(templateId).orElseThrow();
        assertThat(registry.isPublished()).isFalse();
        assertThat(registry.getVersion()).isNotEqualTo(10_001);
    }

    @Test
    @WithMockUser(username = UNKNOWN_LOGIN)
    @DisplayName("BUG-068: saveVersion() is refused for an unknown login and nothing is committed")
    void saveVersion_unknownSecurityLogin_isRefusedAndNothingCommitted() {
        // Arrange
        UUID templateId = createTemplate("BUG-061 SaveVersion Template");

        // Act
        assertThatThrownBy(() -> templateService.saveVersion(
                templateId, new SaveVersionRequest("MINOR", "BUG-061")))
                .as("BUG-068: unknown login must not be allowed to save a version")
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(MODIFY_DENIED)
                .isNotInstanceOf(org.springframework.transaction.UnexpectedRollbackException.class);

        // Assert — версия НЕ создана
        List<TemplateVersion> versions =
                versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId);
        assertThat(versions).isEmpty();
    }

    @Test
    @WithMockUser(username = UNKNOWN_LOGIN)
    @DisplayName("BUG-061: an unknown login is not an error — the audit entry is written with a null user_id")
    void create_unknownSecurityLogin_writesAuditEntryWithNullUserId() {
        // Arrange
        UUID templateId = createTemplate("BUG-061 Audit Template");

        // Assert — аудит пишется асинхронно, поэтому ждём запись.
        // Неизвестный логин не ломает операцию: created_by = null, запись создаётся.
        assertThat(awaitAuditEntry("TEMPLATE", templateId, "CREATE"))
                .as("audit entry CREATE for template %s must be written", templateId)
                .isTrue();
        assertThat(countAuditEntriesWithNullUser("TEMPLATE", templateId, "CREATE"))
                .as("audit entry must have user_id = null for an unknown login")
                .isEqualTo(1);

        // Assert — а вот отказ в изменении шаблона не оставляет записи об успешной публикации
        assertThatCode(() -> templateService.publish(templateId))
                .as("BUG-068: publish() under an unknown login must be refused")
                .isInstanceOf(AccessDeniedException.class);
        assertThat(countAuditEntries("TEMPLATE", templateId, "PUBLISH"))
                .as("a refused publish must not leave a PUBLISH audit entry")
                .isZero();
    }

    @Test
    @WithMockUser(username = UNKNOWN_LOGIN)
    @DisplayName("BUG-061: an unknown login on an unguarded write path commits the transaction and keeps the attribution null")
    void documentWrite_unknownSecurityLogin_commitsWithNullAttribution() {
        // Arrange — опубликованный шаблон: создать документ может и не-владелец.
        // Публикуем от имени администратора (у шаблона нет владельца — guard BUG-068
        // иначе не пустит никого, кроме владельца).
        UUID templateId = createTemplate("BUG-061 Document Template");
        TestAuth.callAs("admin", () -> templateService.publish(templateId));

        // Act — запись в модуле document: он не закрыт guard'ом BUG-068, поэтому
        // неизвестный логин — это не ошибка, а «нет атрибуции».
        AtomicReference<DocumentResponse> created = new AtomicReference<>();
        assertThatCode(() -> created.set(
                documentService.create(templateId, "BUG-061 Document", null, null, null)))
                .as("BUG-061: неизвестный логин не должен помечать транзакцию как rollback-only "
                        + "(операция не должна падать, в т.ч. с UnexpectedRollbackException)")
                .doesNotThrowAnyException();

        UUID documentId = created.get().id();
        documentIds.add(documentId);

        // Assert — операция закоммичена: строка документа в PostgreSQL на месте
        assertThat(countDocumentRegistry(documentId))
                .as("документ должен быть закоммичен")
                .isEqualTo(1);
        // … и атрибуция пустая: владелец неизвестен
        assertThat(documentOwnerId(documentId))
                .as("owner_id документа должен остаться null")
                .isNull();

        // Act — редакция документа: здесь вызывающий ищется по логину внутри
        // транзакции (currentUserId()) — ровно тот путь, который ломался в BUG-061.
        AtomicReference<DocumentRevisionDTO> revision = new AtomicReference<>();
        assertThatCode(() -> revision.set(documentService.saveRevision(documentId, "BUG-061")))
                .as("BUG-061: saveRevision() под неизвестным логином не должен падать "
                        + "(в т.ч. с UnexpectedRollbackException)")
                .doesNotThrowAnyException();

        // Assert — редакция закоммичена, а её автор неизвестен (saved_by = null)
        assertThat(countRevisions(documentId))
                .as("редакция документа должна быть закоммичена")
                .isEqualTo(1);
        assertThat(revision.get().savedBy())
                .as("saved_by редакции должен остаться null")
                .isNull();
        assertThat(revisionSavedBy(documentId))
                .as("saved_by в БД должен остаться null")
                .isNull();

        // Assert — аудит создан, но без автора
        assertThat(awaitAuditEntry("DOCUMENT", documentId, "CREATE"))
                .as("audit entry CREATE for document %s must be written", documentId)
                .isTrue();
        assertThat(countAuditEntriesWithNullUser("DOCUMENT", documentId, "CREATE"))
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

    /** Количество строк реестра документов с данным id. */
    private long countDocumentRegistry(UUID documentId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_registry WHERE id = ?", Long.class, documentId);
        return count == null ? 0L : count;
    }

    /** Владелец документа в БД ({@code null}, если владелец не определён). */
    private UUID documentOwnerId(UUID documentId) {
        return jdbcTemplate.queryForObject(
                "SELECT owner_id FROM document_registry WHERE id = ?", UUID.class, documentId);
    }

    /** Количество редакций документа. */
    private long countRevisions(UUID documentId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_revision WHERE document_id = ?", Long.class, documentId);
        return count == null ? 0L : count;
    }

    /** Автор последней редакции документа в БД ({@code null} — автор неизвестен). */
    private UUID revisionSavedBy(UUID documentId) {
        return jdbcTemplate.queryForObject(
                "SELECT saved_by FROM document_revision WHERE document_id = ? "
                        + "ORDER BY revision_number DESC LIMIT 1", UUID.class, documentId);
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

    private Long countAuditEntries(String entityType, UUID entityId, String action) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log "
                        + "WHERE entity_type = ? AND entity_id = ? AND action = ?",
                Long.class, entityType, entityId, action);
    }
}
