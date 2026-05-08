package com.docforge.document.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@org.springframework.data.mongodb.core.mapping.Document(collection = "documents")
public class Document {

    @Id
    private UUID id;

    @Field("template_id")
    private UUID templateId;

    @Field("template_version")
    private int templateVersion;

    /** UUID снимка шаблона (template_snapshots._id), используемого для генерации. */
    @Field("template_snapshot_id")
    private UUID templateSnapshotId;

    /** Ответы на опросный лист: code → значение */
    private Map<String, Object> answers;

    /**
     * TipTap JSON после генерации (параметры подставлены, условия разрешены).
     * Хранится как произвольная структура — десериализуется в Object (Map/List).
     */
    @Field("generated_body")
    private Object generatedBody;

    /**
     * Актуальное состояние документа после правок пользователя.
     * Обновляется через Yjs CRDT.
     */
    @Field("current_body")
    private Object currentBody;

    /** Yjs state vector для восстановления коллаборации */
    @Field("yjs_state")
    private byte[] yjsState;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public Document() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTemplateId() { return templateId; }
    public void setTemplateId(UUID templateId) { this.templateId = templateId; }

    public int getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(int templateVersion) { this.templateVersion = templateVersion; }

    public UUID getTemplateSnapshotId() { return templateSnapshotId; }
    public void setTemplateSnapshotId(UUID templateSnapshotId) { this.templateSnapshotId = templateSnapshotId; }

    public Map<String, Object> getAnswers() { return answers; }
    public void setAnswers(Map<String, Object> answers) { this.answers = answers; }

    public Object getGeneratedBody() { return generatedBody; }
    public void setGeneratedBody(Object generatedBody) { this.generatedBody = generatedBody; }

    public Object getCurrentBody() { return currentBody; }
    public void setCurrentBody(Object currentBody) { this.currentBody = currentBody; }

    public byte[] getYjsState() { return yjsState; }
    public void setYjsState(byte[] yjsState) { this.yjsState = yjsState; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
