package com.docforge.audit;

import java.util.UUID;

public interface AuditService {

    /**
     * Записывает событие в audit_log.
     *
     * @param login      login текущего пользователя (Spring Security principal); nullable
     * @param entityType тип сущности — "TEMPLATE" или "DOCUMENT"
     * @param entityId   UUID сущности
     * @param action     действие — CREATE, UPDATE, DELETE, PUBLISH, UNPUBLISH, GENERATE, EXPORT
     * @param details    произвольный текст или JSON. Колонка {@code details} —
     *                   {@code JSONB}: валидный JSON сохраняется как есть
     *                   (строка {@code "null"} — как {@code null}), любая другая
     *                   строка, а также текст с NUL и непарными суррогатами
     *                   сохраняется как {@code {"note": "исходный текст"}},
     *                   {@code null} остаётся {@code null} (BUG-071).
     */
    void log(String login, String entityType, UUID entityId, String action, String details);
}
