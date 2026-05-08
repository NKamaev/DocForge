package com.docforge.common.format;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Настройки отображения параметра при подстановке в документ.
 * Хранится как embedded-объект внутри Parameter (MongoDB).
 *
 * <p>Два уровня применения:
 * <ol>
 *   <li>{@code Parameter.format} — дефолт для всех вхождений параметра в шаблоне.</li>
 *   <li>{@code parameterField.attrs.format} — override на конкретной TipTap-ноде (приоритет выше).</li>
 * </ol>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParameterFormat {

    /** Формат даты (только для ParameterType.DATE). */
    private DateFormat dateFormat;

    /** Формат числа (только для ParameterType.NUMBER). */
    private NumberDisplayFormat numberFormat;

    /**
     * Единица измерения — суффикс после числа.
     * Используется в форматах WITH_UNIT, WORDS_WITH_UNIT, WORDS_WITH_NUMBER_AND_UNIT.
     * Примеры: "рублей", "штук", "процентов".
     */
    private String unit;

    /** Формат ФИО (только для ParameterType.FIO). */
    private FioDisplayFormat fioFormat;

    /** Падеж (только для ParameterType.FIO). */
    private GrammaticalCase grammaticalCase;
}
