package com.docforge.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Поля страницы документа в миллиметрах. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageMargins {

    private int top;
    private int right;
    private int bottom;
    private int left;
}
