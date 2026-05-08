package com.docforge.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Шаблон документа. Хранится в MongoDB (коллекция "templates").
 * id совпадает с template_registry.id в PostgreSQL — единый UUID-ключ.
 *
 * Тело шаблона (body) — TipTap/ProseMirror JSON с кастомными нодами:
 *   - parameter_field: подставляемый параметр
 *   - conditional_block: условный блок текста
 */
@Document(collection = "templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Template {

    @Id
    private UUID id;

    private int version;

    private List<Parameter> parameters;

    private Questionnaire questionnaire;

    /**
     * Тело шаблона в формате TipTap JSON (ProseMirror-совместимый).
     * Хранится как произвольный Map, поскольку структура определяется редактором.
     */
    private Map<String, Object> body;

    private DocumentFormatting formatting;

    private List<Script> scripts;

    /** Список формулировок шаблона (null-совместимо со старыми шаблонами). */
    private List<Phrasing> phrasings;
}
