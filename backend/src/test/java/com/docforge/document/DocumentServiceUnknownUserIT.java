package com.docforge.document;

import com.docforge.AbstractIT;
import com.docforge.document.dto.DocumentRevisionDTO;
import com.docforge.document.model.Document;
import com.docforge.document.model.DocumentRevision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * BUG-061 regression: модуль документов не должен отравлять собственную транзакцию,
 * когда {@code saved_by} ревизии указывает на несуществующего (удалённого) пользователя.
 * <p>
 * {@code DocumentServiceImpl.toRevisionDTO()} раньше вызывал бросающий
 * {@code userService.getById(savedBy)} внутри {@code try/catch}. Вложенный вызов
 * присоединялся к транзакции вызывающего метода, бросок
 * {@code ResourceNotFoundException} помечал общую транзакцию как rollback-only, и
 * на коммите бизнес-метод падал с {@code UnexpectedRollbackException} — данные
 * терялись, хотя исключение было «проглочено».
 * <p>
 * Тест намеренно не зависит от модуля шаблонов: все фикстуры создаются напрямую
 * (SQL + MongoRepository), чтобы падение на старом коде могло прийти только из
 * модуля документов.
 * <p>
 * Ожидаемое поведение после фикса: неизвестный автор — это нормальная ситуация,
 * {@code authorName} просто остаётся {@code null}, а транзакция коммитится.
 */
class DocumentServiceUnknownUserIT extends AbstractIT {

    /** Логин, которого гарантированно нет в таблице {@code users}. */
    private static final String UNKNOWN_LOGIN = "bug061-unknown-document-user";

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentRevisionRepository revisionRepository;

    @Autowired
    private DocumentRevisionBodyRepository revisionBodyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    /** Отдельный JDBC-менеджер транзакций — только для подготовки фикстур. */
    private TransactionTemplate jdbcTransaction;

    private final List<UUID> documentIds = new ArrayList<>();
    private final List<UUID> revisionBodyIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbcTransaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        // Предусловие — обычный SQL: он работает и на старом, и на новом коде
        // (userService.findUser() появился только вместе с фиксом BUG-061).
        assertThat(countUsersByLogin(UNKNOWN_LOGIN))
                .as("login %s must not exist in table users", UNKNOWN_LOGIN)
                .isZero();
    }

    @AfterEach
    void tearDown() {
        for (UUID documentId : documentIds) {
            jdbcTemplate.update("DELETE FROM document_revision WHERE document_id = ?", documentId);
            jdbcTemplate.update("DELETE FROM document_registry WHERE id = ?", documentId);
            jdbcTemplate.update("DELETE FROM audit_log WHERE entity_id = ?", documentId);
            documentRepository.deleteById(documentId);
        }
        for (UUID revisionBodyId : revisionBodyIds) {
            revisionBodyRepository.deleteById(revisionBodyId);
        }
        documentIds.clear();
        revisionBodyIds.clear();
    }

    @Test
    @DisplayName("BUG-061: listRevisions() returns the revision and commits when saved_by points to a deleted user")
    void listRevisions_savedByPointsToDeletedUser_returnsRevisionWithNullAuthor() {
        // Arrange — документ и ревизия, автор которой удалён из таблицы users
        UUID documentId = createDocument("BUG-061 Revision Document");
        UUID deletedUserId = UUID.randomUUID();
        insertRevisionWithUnknownAuthor(documentId, 1, deletedUserId);

        // Act — раньше падало с UnexpectedRollbackException на коммите транзакции
        AtomicReference<List<DocumentRevisionDTO>> revisionsRef = new AtomicReference<>();
        assertThatCode(() -> revisionsRef.set(documentService.listRevisions(documentId)))
                .as("BUG-061: listRevisions() must not fail with UnexpectedRollbackException")
                .doesNotThrowAnyException();

        // Assert — ревизия вернулась, но имя неизвестного автора определить нельзя
        List<DocumentRevisionDTO> revisions = revisionsRef.get();
        assertThat(revisions).hasSize(1);
        DocumentRevisionDTO revision = revisions.get(0);
        assertThat(revision.documentId()).isEqualTo(documentId);
        assertThat(revision.editionNumber()).isEqualTo(1);
        assertThat(revision.savedBy()).isEqualTo(deletedUserId);
        assertThat(revision.authorName()).isNull();
    }

    @Test
    @WithMockUser(username = UNKNOWN_LOGIN)
    @DisplayName("BUG-061: saveRevision() commits a revision with a null saved_by for an unknown login")
    void saveRevision_unknownSecurityLogin_commitsRevisionWithNullSavedBy() {
        // Arrange
        UUID documentId = createDocument("BUG-061 Revision Document");

        // Act — неизвестный логин в контексте безопасности не должен ломать транзакцию
        AtomicReference<DocumentRevisionDTO> revisionRef = new AtomicReference<>();
        assertThatCode(() -> revisionRef.set(documentService.saveRevision(documentId, "BUG-061 revision")))
                .as("BUG-061: saveRevision() must not fail with UnexpectedRollbackException")
                .doesNotThrowAnyException();

        // Assert — ревизия закоммичена, автор неизвестен → saved_by = null
        DocumentRevisionDTO revision = revisionRef.get();
        assertThat(revision).isNotNull();
        assertThat(revision.savedBy()).isNull();
        assertThat(revision.authorName()).isNull();
        revisionBodyIds.add(revision.id());

        List<DocumentRevision> revisions =
                revisionRepository.findByDocumentIdOrderByRevisionNumberDesc(documentId);
        assertThat(revisions).hasSize(1);
        assertThat(revisions.get(0).getSavedBy()).isNull();
        assertThat(revisions.get(0).getComment()).isEqualTo("BUG-061 revision");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Создаёт документ: строку в {@code document_registry} и тело в MongoDB.
     * Намеренно без обращения к модулю шаблонов — тест не должен зависеть от него.
     */
    private UUID createDocument(String name) {
        UUID documentId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO document_registry (id, name) VALUES (?, ?)", documentId, name);

        Document document = new Document();
        document.setId(documentId);
        document.setAnswers(new HashMap<>());
        document.setCreatedAt(Instant.now());
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);

        documentIds.add(documentId);
        return documentId;
    }

    /**
     * Вставляет ревизию, у которой {@code saved_by} ссылается на несуществующего
     * пользователя (имитация удалённого автора).
     * <p>
     * На {@code saved_by} есть FK {@code document_revision_saved_by_fkey} → {@code users(id)},
     * поэтому вставка идёт с временно отключённой проверкой FK:
     * {@code SET LOCAL session_replication_role = replica} действует только внутри
     * транзакции и сам восстанавливается при коммите — схема базы не меняется.
     * <p>
     * Требуется суперпользователь БД (в docker-compose пользователь {@code docforge}
     * создаётся как суперпользователь PostgreSQL).
     */
    private void insertRevisionWithUnknownAuthor(UUID documentId, int revisionNumber, UUID unknownAuthorId) {
        jdbcTransaction.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL session_replication_role = replica");
            jdbcTemplate.update(
                    "INSERT INTO document_revision (id, document_id, revision_number, saved_by, comment) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), documentId, revisionNumber, unknownAuthorId,
                    "BUG-061 revision of a deleted user");
        });
    }

    private long countUsersByLogin(String login) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE login = ?", Long.class, login);
        return count == null ? 0L : count;
    }
}
