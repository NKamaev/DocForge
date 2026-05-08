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
     * @param details    произвольный JSON-текст или null
     */
    void log(String login, String entityType, UUID entityId, String action, String details);
}
