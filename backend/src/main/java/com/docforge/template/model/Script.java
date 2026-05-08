package com.docforge.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Python-скрипт из библиотеки шаблона.
 * Скрипт должен устанавливать переменную result (boolean).
 * Доступные переменные в контексте выполнения — значения параметров по их code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Script {

    /** Уникальный идентификатор скрипта в рамках шаблона. Ссылается из Condition.scriptId. */
    private String id;

    private String name;

    /** Python-код скрипта. Должен присваивать: result = <boolean-выражение>. */
    private String code;

    private String description;
}
