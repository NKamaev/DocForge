package com.docforge.template.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Метаданные версии шаблона в PostgreSQL.
 * Каждая запись соответствует одному publish-снимку.
 * Тело снимка хранится в MongoDB (коллекция template_snapshots, _id = id этой записи).
 */
@Entity
@Table(name = "template_version")
@Getter
@Setter
@NoArgsConstructor
public class TemplateVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(columnDefinition = "TEXT")
    private String comment;
}
