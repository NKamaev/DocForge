package com.docforge.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.docforge.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            // «{"a":1}garbage» — это не JSON: без флага readTree молча съедает
            // префикс, сырая строка уходит в jsonb, вставка падает и запись
            // теряется (ровно симптом BUG-071).
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final AuditLogWriter auditLogWriter;
    private final UserService userService;

    /**
     * Пишет событие аудита асинхронно.
     *
     * <p>Транзакция открывается в {@link AuditLogWriter} ({@code REQUIRES_NEW}),
     * а {@code try/catch} здесь — <b>вне</b> транзакции (BUG-071): если бы
     * catch жил внутри {@code @Transactional}-метода, упавшая вставка пометила
     * бы транзакцию rollback-only, и {@code commit} бросил бы
     * {@code UnexpectedRollbackException} — запись потерялась бы, а наверх
     * ушла бы неверная ошибка.
     */
    @Async
    @Override
    public void log(String login, String entityType, UUID entityId, String action, String details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setAction(action);
            entry.setDetails(normalizeDetails(details));

            if (login != null) {
                // findUser() не бросает исключений: неизвестный логин — это
                // нормальная ситуация (пользователь мог быть удалён).
                userService.findUser(login).ifPresent(u -> entry.setUserId(u.id()));
            }

            auditLogWriter.write(entry);
        } catch (Exception e) {
            // Audit must never break business operations
            log.error("Failed to write audit log [{} {} {}]: {}", action, entityType, entityId, e.getMessage());
        }
    }

    /**
     * Приводит {@code details} к валидному JSON: колонка {@code audit_log.details}
     * — {@code JSONB}, а вызывающие передают произвольный текст
     * (например {@code "version=1.0,bumpType=MINOR"}), из-за чего вставка падала
     * и запись аудита терялась (BUG-071).
     *
     * <p>Валидный JSON возвращается как есть; пустая строка, мусор после
     * значения и любая другая невалидная строка оборачиваются в
     * {@code {"note": "<текст>"}}; {@code null} и строка {@code "null"}
     * становятся {@code null} (SQL NULL).
     */
    private String normalizeDetails(String details) {
        if (details == null) {
            return null;
        }
        String text = sanitize(details);
        // «\u0000» и непарные суррогаты в escape-записи jsonb отвергает —
        // такие строки оборачиваем, а не пробрасываем как есть.
        if (hasJsonbUnsafeEscape(text)) {
            return wrap(text);
        }
        try {
            JsonNode node = MAPPER.readTree(text);
            // «», «   » и мусор после значения не бросают исключение, но и не
            // являются валидным JSON для jsonb — такие строки оборачиваем.
            if (node == null || node.isMissingNode()) {
                return wrap(text);
            }
            if (node.isNull()) {
                // «null» — это валидный JSON, но удобнее хранить SQL NULL.
                return null;
            }
            return text;
        } catch (JsonProcessingException notJson) {
            return wrap(text);
        }
    }

    /**
     * Убирает символы, запрещённые в {@code jsonb}: NUL и непарные
     * суррогаты. {@code writeValueAsString} экранирует их в
     * {@code \u0000}/суррогатные escape-последовательности, которые PostgreSQL
     * отвергает — поэтому их нужно вырезать до сериализации.
     */
    private String sanitize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u0000') {
                continue;
            }
            if (Character.isHighSurrogate(c)) {
                char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
                if (Character.isLowSurrogate(next)) {
                    sb.append(c).append(next);
                    i++;
                }
                continue;
            }
            if (Character.isLowSurrogate(c)) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Есть ли в тексте escape-последовательность, которую {@code jsonb} отвергает. */
    private boolean hasJsonbUnsafeEscape(String text) {
        int idx = text.indexOf("\\u");
        while (idx >= 0 && idx + 6 <= text.length()) {
            try {
                int code = Integer.parseInt(text.substring(idx + 2, idx + 6), 16);
                if (code == 0 || (code >= 0xD800 && code <= 0xDFFF)) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // не escape-последовательность — просто текст
            }
            idx = text.indexOf("\\u", idx + 2);
        }
        return false;
    }

    /** Оборачивает произвольный текст в {@code {"note": "<текст>"}}. */
    private String wrap(String details) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("note", details);
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            log.warn("Не удалось сериализовать details аудита: {}", e.getMessage());
            return null;
        }
    }
}
