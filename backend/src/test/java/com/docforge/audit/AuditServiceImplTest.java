package com.docforge.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.docforge.user.UserService;
import com.docforge.user.dto.UserDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BUG-071 regression: контракт {@link AuditServiceImpl#log} — запись аудита никогда
 * не ломает бизнес-операцию, а {@code details} гарантированно является валидным JSON
 * (колонка {@code audit_log.details} — {@code jsonb}).
 *
 * <p>Раньше {@code log(...)} был {@code @Transactional(REQUIRES_NEW)} с
 * {@code try/catch} <em>внутри</em> транзакции: упавшая вставка помечала транзакцию
 * как rollback-only, {@code catch} глушил исключение, но {@code commit} всё равно
 * бросал {@code UnexpectedRollbackException} — запись терялась, а наверх уходила
 * неверная ошибка. Реальный триггер падения вставки: вызывающие передают в
 * {@code details} произвольный текст (например {@code "version=1.0,bumpType=MINOR"}),
 * который не является JSON.
 *
 * <p>Теперь транзакция живёт в отдельном бине ({@link AuditLogWriter}), {@code try/catch}
 * — вне транзакции, а {@code normalizeDetails()} оборачивает невалидный JSON в
 * {@code {"note": "<текст>"}}.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock private AuditLogWriter auditLogWriter;
    @Mock private UserService userService;

    @InjectMocks
    private AuditServiceImpl auditService;

    private ListAppender<ILoggingEvent> logAppender;

    @AfterEach
    void detachLogAppender() {
        if (logAppender != null) {
            auditLogger().detachAppender(logAppender);
            logAppender = null;
        }
    }

    // ── normalizeDetails() через log(...) ─────────────────────────────────────

    @Test
    @DisplayName("BUG-071: non-JSON details are wrapped as {\"note\": \"<текст>\"} before the insert")
    void log_nonJsonDetails_wrapsTextIntoNoteJson() {
        // given — ровно тот текст, который пишет TemplateServiceImpl.saveVersion()
        UUID templateId = UUID.randomUUID();

        // when
        auditService.log("some-login", "TEMPLATE", templateId, "SAVE_VERSION",
                "version=1.0,bumpType=MINOR");

        // then — в writer уходит валидный JSON, а не сырая строка
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).write(captor.capture());
        AuditLog entry = captor.getValue();

        assertThat(entry.getDetails())
                .as("details должны быть обёрнуты в JSON-объект {\"note\": ...}")
                .isEqualTo("{\"note\":\"version=1.0,bumpType=MINOR\"}");
        assertThat(entry.getEntityType()).isEqualTo("TEMPLATE");
        assertThat(entry.getEntityId()).isEqualTo(templateId);
        assertThat(entry.getAction()).isEqualTo("SAVE_VERSION");
    }

    @Test
    @DisplayName("BUG-071: valid JSON details are stored unchanged")
    void log_validJsonDetails_storedUnchanged() {
        // given
        UUID templateId = UUID.randomUUID();

        // when
        auditService.log(null, "TEMPLATE", templateId, "UPDATE", "{\"a\":1}");

        // then — валидный JSON не перепаковывается
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).write(captor.capture());
        assertThat(captor.getValue().getDetails()).isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("BUG-071: null details stay null")
    void log_nullDetails_staysNull() {
        // given
        UUID documentId = UUID.randomUUID();

        // when
        auditService.log(null, "DOCUMENT", documentId, "CREATE", null);

        // then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).write(captor.capture());
        assertThat(captor.getValue().getDetails()).isNull();
    }

    // ── normalizeDetails(): edge cases (BUG-071) ─────────────────────────────

    @Test
    @DisplayName("BUG-071: empty details are wrapped as {\"note\": \"\"} — never stored raw")
    void log_emptyDetails_wrapsIntoNoteJson() {
        // given
        UUID templateId = UUID.randomUUID();

        // when
        auditService.log(null, "TEMPLATE", templateId, "CREATE", "");

        // then — «» не является валидным JSON и НЕ должна уйти в jsonb как есть
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).write(captor.capture());
        assertThat(captor.getValue().getDetails())
                .as("пустая строка должна быть обёрнута в {\"note\": \"\"}, а не сохранена сырой")
                .isEqualTo("{\"note\":\"\"}");
    }

    @Test
    @DisplayName("BUG-071: blank details are wrapped as {\"note\": \"   \"} — never stored raw")
    void log_blankDetails_wrapsIntoNoteJson() {
        // given
        UUID templateId = UUID.randomUUID();

        // when
        auditService.log(null, "TEMPLATE", templateId, "CREATE", "   ");

        // then — пробелы тоже не валидный JSON для jsonb
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).write(captor.capture());
        assertThat(captor.getValue().getDetails())
                .as("строка из пробелов должна быть обёрнута в {\"note\": \"   \"}")
                .isEqualTo("{\"note\":\"   \"}");
    }

    @Test
    @DisplayName("BUG-071: trailing garbage after a JSON value is wrapped as {\"note\": \"<текст>\"} — never stored raw")
    void log_trailingGarbageDetails_wrapsRawTextIntoNoteJson() throws Exception {
        // given — FAIL_ON_TRAILING_TOKENS: «{"a":1}garbage» — это не JSON,
        // мусор после значения не должен «съедаться» молча
        String raw = "{\"a\":1}garbage";

        // when
        auditService.log(null, "TEMPLATE", UUID.randomUUID(), "UPDATE", raw);

        // then — не сырой текст и не голый префикс «{"a":1}»
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).write(captor.capture());
        String stored = captor.getValue().getDetails();

        assertThat(stored)
                .as("мусор после значения должен быть целиком обёрнут в {\"note\": ...}")
                .isEqualTo("{\"note\":\"{\\\"a\\\":1}garbage\"}");

        // страховка: сохранённое значение — валидный JSON-объект {"note": "<исходный текст>"}
        JsonNode parsed = new ObjectMapper().readTree(stored);
        assertThat(parsed.isObject())
                .as("сохранённое details должно быть JSON-объектом")
                .isTrue();
        assertThat(parsed.get("note").asText())
                .as("исходный текст должен быть целиком сохранён в note")
                .isEqualTo(raw);
    }

    @Test
    @DisplayName("BUG-071: \"null\" details are stored as SQL NULL, not as a JSON null literal")
    void log_jsonNullDetails_storedAsSqlNull() {
        // given
        UUID documentId = UUID.randomUUID();

        // when
        auditService.log(null, "DOCUMENT", documentId, "CREATE", "null");

        // then — «null» — валидный JSON, но хранится как SQL NULL, а не как «null»
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).write(captor.capture());
        assertThat(captor.getValue().getDetails())
                .as("строка \"null\" должна дать SQL NULL, а не JSON-литерал null")
                .isNull();
    }

    @Test
    @DisplayName("BUG-071: a JSON string scalar (\"\\\"quoted\\\"\") is stored unchanged")
    void log_jsonStringScalarDetails_storedUnchanged() {
        // given
        UUID templateId = UUID.randomUUID();

        // when
        auditService.log(null, "TEMPLATE", templateId, "CREATE", "\"quoted\"");

        // then — строковый скаляр — валидный JSON, не перепаковывается
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).write(captor.capture());
        assertThat(captor.getValue().getDetails())
                .as("JSON-строковый скаляр должен сохраняться как есть")
                .isEqualTo("\"quoted\"");
    }

    @Test
    @DisplayName("BUG-071: a JSON number scalar (\"42\") is stored unchanged")
    void log_jsonNumberScalarDetails_storedUnchanged() {
        // given
        UUID templateId = UUID.randomUUID();

        // when
        auditService.log(null, "TEMPLATE", templateId, "CREATE", "42");

        // then — числовой скаляр — валидный JSON, не перепаковывается
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).write(captor.capture());
        assertThat(captor.getValue().getDetails())
                .as("JSON-числовой скаляр должен сохраняться как есть")
                .isEqualTo("42");
    }

    // ── normalizeDetails(): jsonb-unsafe символы (BUG-071) ───────────────────

    @Test
    @DisplayName("BUG-071: details with a \\u0000 escape are wrapped as {\"note\": ...} — jsonb rejects that escape")
    void log_detailsWithNulEscape_wrapsIntoNoteJson() throws Exception {
        // given — валидный JSON, но с escape \u0000, который PostgreSQL jsonb отвергает
        String raw = "{\"a\":\"\\u0000\"}";

        // when
        auditService.log(null, "TEMPLATE", UUID.randomUUID(), "UPDATE", raw);

        // then — строка не уходит в jsonb как есть, а оборачивается в {"note": ...}
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).write(captor.capture());
        String stored = captor.getValue().getDetails();

        assertThat(stored)
                .as("детали с escape \\u0000 должны быть обёрнуты, а не сохранены сырыми")
                .isNotEqualTo(raw);

        // сохранённое значение — валидный JSON-объект {"note": "<исходный текст>"}
        JsonNode parsed = new ObjectMapper().readTree(stored);
        assertThat(parsed.isObject())
                .as("сохранённое details должно быть JSON-объектом")
                .isTrue();
        assertThat(parsed.get("note").asText())
                .as("исходный текст должен быть целиком сохранён в note")
                .isEqualTo(raw);

        // страховка: сохранённое значение валидно для колонки jsonb — ни «живого»
        // NUL-символа, ни escape \u0000 на уровне JSON (после декодирования escape
        // Jackson даёт ровно тот же NUL, что и jsonb). Сырой текст «\u0000» в note —
        // это экранированный обратный слэш + «u0000»: в сохранённом значении он
        // записан как \\u0000 и jsonb такое принимает.
        assertThat(stored)
                .as("в сохранённом значении не должно быть NUL-символа")
                .doesNotContain("\u0000");
        assertThat(parsed.get("note").asText())
                .as("escape \\u0000 не должен пережить декодирование как NUL-символ")
                .doesNotContain("\u0000");
    }

    @Test
    @DisplayName("BUG-071: a literal NUL character is stripped and the text is wrapped as {\"note\": ...}")
    void log_detailsWithLiteralNul_strippedAndWrappedIntoNoteJson() throws Exception {
        // given — сырой (не JSON) текст с «живым» NUL-символом (U+0000)
        String raw = "\u0000";

        // when
        auditService.log(null, "TEMPLATE", UUID.randomUUID(), "CREATE", raw);

        // then — NUL вырезан до сохранения, а текст обёрнут в {"note": ...}
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).write(captor.capture());
        String stored = captor.getValue().getDetails();

        assertThat(stored)
                .as("пустой после вырезания NUL текст должен быть обёрнут в {\"note\": \"\"}")
                .isEqualTo("{\"note\":\"\"}");
        assertThat(stored)
                .as("NUL-символ не должен попасть в сохранённое значение")
                .doesNotContain("\u0000")
                .isNotEqualTo(raw);

        // страховка: сохранённое значение парсится как JSON и безопасно для jsonb
        JsonNode parsed = new ObjectMapper().readTree(stored);
        assertThat(parsed.isObject())
                .as("сохранённое details должно быть JSON-объектом")
                .isTrue();
        assertThat(parsed.get("note").asText())
                .as("в note не должно остаться NUL-символа")
                .doesNotContain("\u0000");
    }

    // ── атрибуция пользователя ───────────────────────────────────────────────

    @Test
    @DisplayName("BUG-061: an unknown login leaves userId null and does not throw")
    void log_unknownLogin_writesEntryWithNullUserId() {
        // given
        when(userService.findUser("ghost-user")).thenReturn(Optional.empty());
        UUID templateId = UUID.randomUUID();

        // when — неизвестный логин не является ошибкой
        assertThatCode(() -> auditService.log("ghost-user", "TEMPLATE", templateId, "CREATE", null))
                .doesNotThrowAnyException();

        // then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).write(captor.capture());
        assertThat(captor.getValue().getUserId())
                .as("user_id должен остаться null для неизвестного логина")
                .isNull();
    }

    @Test
    @DisplayName("a known login resolves the user id on the audit entry")
    void log_knownLogin_writesEntryWithUserId() {
        // given
        UUID userId = UUID.randomUUID();
        when(userService.findUser("known-user")).thenReturn(Optional.of(
                new UserDTO(userId, "known-user", "Known User", null, false, null)));

        // when
        auditService.log("known-user", "TEMPLATE", UUID.randomUUID(), "CREATE", null);

        // then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).write(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    // ── сбой записи аудита ───────────────────────────────────────────────────

    @Test
    @DisplayName("BUG-071: a failure inside AuditLogWriter is swallowed and logged — the caller never sees it")
    void log_writerFailure_isSwallowedAndLogged() {
        // given — вставка падает (например, нарушение ограничения БД)
        doThrow(new IllegalStateException("insert failed"))
                .when(auditLogWriter).write(any());
        logAppender = attachToAuditLogger();

        // when — бизнес-вызывающий не должен увидеть исключение
        assertThatCode(() -> auditService.log(null, "TEMPLATE", UUID.randomUUID(), "CREATE", null))
                .as("аудит не должен ломать бизнес-операцию")
                .doesNotThrowAnyException();

        // then — сбой залогирован как ERROR с причиной
        assertThat(logAppender.list)
                .as("сбой записи аудита должен быть залогирован")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage()).contains("insert failed");
                });
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static ch.qos.logback.classic.Logger auditLogger() {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AuditServiceImpl.class);
    }

    /** Вешает на логгер {@link AuditServiceImpl} перехватчик сообщений. */
    private ListAppender<ILoggingEvent> attachToAuditLogger() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        auditLogger().addAppender(appender);
        return appender;
    }
}
