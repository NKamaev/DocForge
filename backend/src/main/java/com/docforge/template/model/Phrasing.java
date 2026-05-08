package com.docforge.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Именованный список формулировок — вариантов инлайн-текста с условиями показа.
 * Хранится в массиве phrasings внутри Template (MongoDB).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Phrasing {
    private String id;
    private String name;
    private String description;
    private List<PhrasingVariant> variants;
}
