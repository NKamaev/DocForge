package com.docforge.audit;

import com.docforge.AbstractIT;
import com.docforge.AuditTestSupport;
import com.docforge.TestAuth;
import com.docforge.template.TemplateService;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.RollbackResponse;
import com.docforge.template.dto.SaveVersionRequest;
import com.docforge.template.dto.SaveVersionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * BUG-071 regression: бизнес-операция, пишущая в аудит <em>не-JSON</em> {@code details},
 * оставляет строку в {@code audit_log} и не падает с
 * {@code UnexpectedRollbackException}.
 *
 * <p>Раньше {@code AuditServiceImpl.log(...)} был {@code @Transactional(REQUIRES_NEW)}
 * с {@code try/catch} внутри транзакции, а {@code audit_log.details} — колонка
 * {@code jsonb}. Вызывающие передают туда произвольный текст
 * ({@code TemplateServiceImpl.saveVersion} → {@code "version=…,bumpType=MINOR"},
 * {@code rollback} → {@code "fromVersion=…,newVersion=…"}), вставка падала с
 * {@code invalid input syntax for type json}, транзакция становилась rollback-only,
 * и строка аудита молча терялась (наблюдалось в продакшн-логах).</p>
 *
 * <p>Тест выполняет настоящие бизнес-операции через {@link TemplateService} и
 * проверяет через {@link JdbcTemplate}, что строка аудита появилась, а в
 * {@code details} лежит валидный JSON вида {@code {"note": "<исходный текст>"}}.
 * Аудит пишется асинхронно ({@code @Async}), поэтому строка ожидается поллингом
 * ({@link AuditTestSupport#awaitAuditDetails}).</p>
 */
class AuditNonJsonDetailsIT extends AbstractIT {

    /** Администратор из V014 — владелец прав на изменение шаблона (BUG-068). */
    private static final String ADMIN_LOGIN = "admin";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private TemplateService templateService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    /** Шаблоны, созданные тестом — для очистки в {@code @AfterEach}. */
    private final List<UUID> templateIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID templateId : templateIds) {
            // Аудит пишется асинхронно: дожидаемся «затихания» записей по сущности,
            // иначе поздняя строка останется в БД после очистки.
            AuditTestSupport.awaitQuiescence(jdbcTemplate,
                    "SELECT COUNT(*) FROM audit_log WHERE entity_id = ?", templateId);
            jdbcTemplate.update("DELETE FROM audit_log WHERE entity_id = ?", templateId);
            jdbcTemplate.update("DELETE FROM template_version WHERE template_id = ?", templateId);
            jdbcTemplate.update("DELETE FROM template_registry WHERE id = ?", templateId);
            mongoTemplate.remove(Query.query(Criteria.where("template_id").is(templateId)), "template_snapshots");
            mongoTemplate.remove(Query.query(Criteria.where("_id").is(templateId)), "templates");
        }
        templateIds.clear();
    }

    @Test
    @DisplayName("BUG-071: saveVersion() with non-JSON audit details leaves an audit row wrapped as {\"note\": …}")
    void saveVersion_nonJsonDetails_writesAuditRowWithNoteJson() {
        // Arrange
        UUID templateId = createTemplate("BUG-071 saveVersion audit");

        // Act — details = "version=1.1,bumpType=MINOR" (не JSON) роняли вставку в jsonb
        assertThatCode(() -> TestAuth.runAs(ADMIN_LOGIN, () ->
                templateService.saveVersion(templateId, new SaveVersionRequest("MINOR", "BUG-071"))))
                .as("BUG-071: saveVersion() не должен падать (в т.ч. с UnexpectedRollbackException)")
                .doesNotThrowAnyException();

        // Assert — строка аудита появилась и details — валидный JSON
        String details = AuditTestSupport.awaitAuditDetails(
                jdbcTemplate, "TEMPLATE", templateId, "SAVE_VERSION");
        JsonNode node = parseDetails(details);
        assertThat(node.isObject())
                .as("details должны быть JSON-объектом, а не сырым текстом: %s", details)
                .isTrue();
        assertThat(node.get("note").asText())
                .as("не-JSON текст должен быть сохранён как {\"note\": \"<текст>\"}")
                .isEqualTo("version=1.1,bumpType=MINOR");
    }

    @Test
    @DisplayName("BUG-071: rollback() with non-JSON audit details leaves an audit row wrapped as {\"note\": …}")
    void rollback_nonJsonDetails_writesAuditRowWithNoteJson() {
        // Arrange — версия, к которой можно откатиться
        UUID templateId = createTemplate("BUG-071 rollback audit");
        SaveVersionResponse saved = TestAuth.callAs(ADMIN_LOGIN, () ->
                templateService.saveVersion(templateId, new SaveVersionRequest("MINOR", "BUG-071")));
        int savedVersionNumber = saved.version().versionNumber();

        // Act — details = "fromVersion=…,newVersion=…" (не JSON) роняли вставку в jsonb
        RollbackResponse[] rolledBack = new RollbackResponse[1];
        assertThatCode(() -> rolledBack[0] = TestAuth.callAs(ADMIN_LOGIN, () ->
                templateService.rollback(templateId, savedVersionNumber)))
                .as("BUG-071: rollback() не должен падать (в т.ч. с UnexpectedRollbackException)")
                .doesNotThrowAnyException();

        // Assert — строка аудита появилась и details — валидный JSON
        String details = AuditTestSupport.awaitAuditDetails(
                jdbcTemplate, "TEMPLATE", templateId, "ROLLBACK");
        JsonNode node = parseDetails(details);
        assertThat(node.isObject())
                .as("details должны быть JSON-объектом, а не сырым текстом: %s", details)
                .isTrue();
        assertThat(node.get("note").asText())
                .as("не-JSON текст должен быть сохранён как {\"note\": \"<текст>\"}")
                .isEqualTo("fromVersion=" + savedVersionNumber
                        + ",newVersion=" + rolledBack[0].version().majorVersion()
                        + "." + rolledBack[0].version().minorVersion());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Создаёт шаблон от имени администратора и запоминает его для очистки. */
    private UUID createTemplate(String name) {
        UUID templateId = TestAuth.callAs(ADMIN_LOGIN, () ->
                templateService.create(null, new CreateTemplateRequest(name, null, null, null))).id();
        templateIds.add(templateId);
        return templateId;
    }

    /**
     * Разбирает {@code details::text}. Если бы в колонку попал невалидный JSON,
     * строка вообще не была бы вставлена — уже сам факт наличия строки и успешный
     * парсинг доказывают, что запись не потеряна (BUG-071).
     */
    private static JsonNode parseDetails(String details) {
        assertThat(details)
                .as("details не должны быть пустыми")
                .isNotNull();
        try {
            return MAPPER.readTree(details);
        } catch (IOException e) {
            throw new AssertionError("details не являются валидным JSON: " + details, e);
        }
    }
}
