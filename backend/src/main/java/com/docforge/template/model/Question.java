package com.docforge.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Вопрос опросного листа, привязанный к параметру шаблона.
 * visibilityCondition == null означает, что вопрос всегда видим.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    /** Уникальный идентификатор вопроса в рамках шаблона (например, "q1"). */
    private String id;

    /** Код параметра из Template.parameters, к которому привязан вопрос. */
    @Field("parameter_code")
    private String parameterCode;

    private String label;

    /** Подсказка под полем ввода. */
    private String hint;

    /** Порядок отображения внутри страницы. */
    private int order;

    /** Условие видимости; null — вопрос показывается всегда. */
    @Field("visibility_condition")
    private Condition visibilityCondition;
}
