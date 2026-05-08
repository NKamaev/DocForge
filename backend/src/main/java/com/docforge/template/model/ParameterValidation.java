package com.docforge.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Опциональные правила валидации для параметра.
 * min/max — для NUMBER, pattern — regex для TEXT.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParameterValidation {

    private Double min;
    private Double max;
    private String pattern;
}
