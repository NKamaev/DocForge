package com.docforge.template.model;

import com.docforge.common.format.ParameterFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * Параметр шаблона — именованный слот для подстановки значения в тело документа.
 * Код параметра уникален в рамках шаблона и используется в TipTap-нодах
 * parameter_field и conditional_block.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Parameter {

    /** Уникальный код параметра (snake_case), например "client_name". */
    private String code;

    /** Человекочитаемое название для UI. */
    private String label;

    private ParameterType type;

    private boolean required;

    /**
     * Значение по умолчанию. Поддерживает спец-константы: $TODAY, $TOMORROW.
     * Хранится как Object — может быть String, Boolean, Number.
     */
    @Field("default_value")
    private Object defaultValue;

    /** Допустимые варианты для type=SELECT. */
    private List<String> options;

    private ParameterValidation validation;

    // ── DIRECTORY type ────────────────────────────────────────────────────────

    /** UUID справочника (только для type=DIRECTORY). */
    @Field("directory_id")
    private String directoryId;

    /** Ключи полей справочника, используемых в шаблоне (только для type=DIRECTORY). */
    @Field("directory_fields")
    private List<String> directoryFields;

    /** Настройки отображения параметра при подстановке. null = системные defaults. */
    private ParameterFormat format;
}
