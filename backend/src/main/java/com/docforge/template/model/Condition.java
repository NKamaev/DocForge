package com.docforge.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Условие видимости вопроса или блока текста.
 * SIMPLE: выражение в поле expression (например, "has_penalty == true").
 * PYTHON: ссылка на скрипт через scriptId + инлайн-код в expression.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Condition {

    private ConditionType type;

    /** Для SIMPLE — логическое выражение. Для PYTHON — инлайн-код скрипта. */
    private String expression;

    /** Для PYTHON — ссылка на Script.id из библиотеки скриптов шаблона. */
    @Field("script_id")
    private String scriptId;
}
