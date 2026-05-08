package com.docforge.document.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Снимок тела документа (ревизия) в MongoDB.
 * _id совпадает с document_revision.id в PostgreSQL.
 */
@org.springframework.data.mongodb.core.mapping.Document(collection = "document_revisions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRevisionBody {

    @Id
    private UUID id;

    @Field("document_id")
    private UUID documentId;

    @Field("revision_number")
    private int revisionNumber;

    /** Снимок current_body на момент сохранения ревизии */
    private Object body;

    /** Снимок answers на момент сохранения ревизии */
    private Map<String, Object> answers;

    @Field("saved_by")
    private UUID savedBy;

    @Field("saved_at")
    private Instant savedAt;

    private String comment;
}
