package com.docforge.document.dto;

import java.util.List;

/**
 * Опросный лист с вычисленной видимостью вопросов и текущими ответами.
 * Возвращается из GET /questionnaire и PUT /questionnaire/answers.
 */
public record QuestionnaireResponse(String title, List<PageItem> pages) {

    public record PageItem(String id, String title, List<QuestionItem> questions) {}

    public record QuestionItem(
            String id,
            String parameterCode,
            String label,
            String hint,
            int order,
            /** Тип параметра в нижнем регистре: text, number, date, fio, boolean, select, directory */
            String type,
            /** Допустимые варианты для type=select */
            List<String> options,
            /** Результат вычисления visibility_condition */
            boolean visible,
            boolean required,
            Object currentValue,
            /** UUID справочника (только для type=directory) */
            String directoryId,
            /** Ключи полей справочника (только для type=directory) */
            List<String> directoryFields
    ) {}
}
