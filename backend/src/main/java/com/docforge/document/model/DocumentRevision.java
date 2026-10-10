package com.docforge.document.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Метаданные редакции документа в PostgreSQL.
 * Тело редакции хранится в MongoDB (коллекция document_revisions, _id = id этой записи).
 */
@Entity
@Table(name = "document_revision")
@Getter
@Setter
@NoArgsConstructor
public class DocumentRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "saved_by")
    private UUID savedBy;

    @Column(name = "saved_at", updatable = false)
    private OffsetDateTime savedAt = OffsetDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String comment;
}
