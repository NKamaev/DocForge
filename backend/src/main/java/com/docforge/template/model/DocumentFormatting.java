package com.docforge.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/** Параметры форматирования документа при экспорте в DOCX/PDF. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentFormatting {

    /** Размер страницы: "A4", "A3", "Letter" и т.д. */
    @Field("page_size")
    private String pageSize;

    /** Ориентация страницы: "portrait" (книжная) или "landscape" (альбомная). */
    @Field("orientation")
    private String orientation;

    private PageMargins margins;

    @Field("default_font")
    private String defaultFont;

    @Field("default_font_size")
    private int defaultFontSize;

    @Field("line_spacing")
    private double lineSpacing;
}
