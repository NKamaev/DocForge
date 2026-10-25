package com.docforge.export;

/**
 * Классификация типов узлов TipTap для экспорта (BUG-078): единый список для
 * DOCX ({@code DocxExportServiceImpl}) и PDF ({@code PdfExportServiceImpl}),
 * чтобы рендеры двух экспортеров не разъезжались. Раньше эти списки были
 * продублированы в обоих сервисах и могли разойтись при добавлении типов.
 */
public final class TipTapNodeTypes {

    private TipTapNodeTypes() {
    }

    /** Инлайн-типы узлов (рендерятся как inline-контент абзаца). */
    public static boolean isInlineType(String type) {
        return switch (String.valueOf(type)) {
            case "text", "hardBreak", "parameterField", "templateParam",
                 "clauseRef", "conditionalInline", "phrasingsRef" -> true;
            default -> false;
        };
    }

    /** Блочные типы узлов (рендерятся как самостоятельные блоки). */
    public static boolean isBlockType(String type) {
        return switch (String.valueOf(type)) {
            case "doc", "paragraph", "heading", "bulletList", "orderedList",
                 "numberedClause", "table", "horizontalRule", "pageBreak",
                 "conditionalBlock" -> true;
            default -> false;
        };
    }
}
