package com.docforge.document.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Устаревший класс — таблица document_participants переименована в document_members (V012).
 * Используется только как legacy DTO для обратной совместимости с DocumentParticipantDTO.
 * Не является JPA-сущностью.
 */
@Getter
@Setter
@NoArgsConstructor
public class DocumentParticipant {

    private DocumentParticipantId id;

    private String role = "EDITOR";

    private OffsetDateTime joinedAt = OffsetDateTime.now();
}
