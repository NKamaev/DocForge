package com.docforge.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Один вариант формулировки.
 * condition == null → вариант-умолчание (показывается, если ни один из предыдущих не подошёл).
 * content — список инлайн TipTap-нодов (text, parameterField, clauseRef, ...).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhrasingVariant {
    private String id;
    /** null = дефолтный вариант (всегда подходит). */
    private Condition condition;
    private List<Map<String, Object>> content;
}
