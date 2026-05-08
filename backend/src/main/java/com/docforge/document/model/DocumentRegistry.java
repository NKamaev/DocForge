package com.docforge.document.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA-сущность — реестр документов в PostgreSQL.
 * id совпадает с _id MongoDB-документа в коллекции "documents".
 * Схема задана миграцией V003__document_registry.sql.
 */
@Entity
@Table(name = "document_registry")
public class DocumentRegistry {

    /** UUID совпадает с _id в MongoDB — генерируется приложением, не БД */
    @Id
    private UUID id;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "owner_id")
    private UUID ownerId;

    /** UUID снимка шаблона (template_version.id), зафиксированного при создании документа. */
    @Column(name = "template_snapshot_id")
    private UUID templateSnapshotId;

    /** Номер версии шаблона для отображения в UI. */
    @Column(name = "template_version_number")
    private Integer templateVersionNumber;

    /**
     * DRAFT → GENERATED → (редактирование) → EXPORTED
     * Значения хранятся как строки; допустимые значения: DRAFT, GENERATED, EXPORTED.
     */
    @Column(name = "status", length = 50)
    private String status = "DRAFT";

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "folder_id")
    private UUID folderId;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public DocumentRegistry() {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTemplateId() { return templateId; }
    public void setTemplateId(UUID templateId) { this.templateId = templateId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }

    public UUID getTemplateSnapshotId() { return templateSnapshotId; }
    public void setTemplateSnapshotId(UUID templateSnapshotId) { this.templateSnapshotId = templateSnapshotId; }

    public Integer getTemplateVersionNumber() { return templateVersionNumber; }
    public void setTemplateVersionNumber(Integer templateVersionNumber) { this.templateVersionNumber = templateVersionNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public UUID getFolderId() { return folderId; }
    public void setFolderId(UUID folderId) { this.folderId = folderId; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
