package com.docforge.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Неизменяемый снимок шаблона в MongoDB (коллекция "template_snapshots").
 * Создаётся при каждой публикации шаблона.
 * _id совпадает с template_version.id в PostgreSQL.
 */
@Document(collection = "template_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateSnapshot {

    @Id
    private UUID id;

    @Field("template_id")
    private UUID templateId;

    @Field("version_number")
    private int versionNumber;

    private List<Parameter> parameters;

    private Questionnaire questionnaire;

    private Map<String, Object> body;

    private DocumentFormatting formatting;

    private List<Script> scripts;

    private List<Phrasing> phrasings;

    @Field("created_at")
    private Instant createdAt;
}
