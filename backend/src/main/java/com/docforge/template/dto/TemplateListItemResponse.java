package com.docforge.template.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Элемент списка шаблонов.
 *
 * <p>{@code ownerId} добавлен в BUG-068: без него клиент не может отличить
 * свой шаблон от чужого и показывает кнопки публикации/удаления для чужих
 * шаблонов (сервер всё равно отклонит запрос — 403).
 */
public record TemplateListItemResponse(
        UUID id,
        String code,
        String name,
        String description,
        String category,
        boolean published,
        UUID ownerId,
        int majorVersion,
        int minorVersion,
        OffsetDateTime createdAt
) {}
