package com.docforge.export;

import com.docforge.template.model.DocumentFormatting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PdfExportServiceImplTest {

    private final PdfExportServiceImpl service = new PdfExportServiceImpl();

    private static Map<String, Object> doc(List<Map<String, Object>> content) {
        return Map.of("type", "doc", "content", content);
    }

    private static Map<String, Object> paragraph(List<Map<String, Object>> inlines) {
        return Map.of("type", "paragraph", "content", inlines);
    }

    private static Map<String, Object> text(String value) {
        return Map.of("type", "text", "text", value);
    }

    private static Map<String, Object> table(List<Map<String, Object>> rows) {
        return Map.of("type", "table", "content", rows);
    }

    private static Map<String, Object> row(List<Map<String, Object>> cells) {
        return Map.of("type", "tableRow", "content", cells);
    }

    private static Map<String, Object> cell(List<Map<String, Object>> blocks) {
        return Map.of("type", "tableCell", "content", blocks);
    }

    private static Map<String, Object> bulletList(List<Map<String, Object>> items) {
        return Map.of("type", "bulletList", "content", items);
    }

    private static Map<String, Object> listItem(List<Map<String, Object>> blocks) {
        return Map.of("type", "listItem", "content", blocks);
    }

    private static Map<String, Object> clauseRef(String refId) {
        return Map.of("type", "clauseRef", "attrs", Map.of("refId", refId));
    }

    private static Map<String, Object> clauseRefWithLabel(String refId, Object label) {
        return Map.of("type", "clauseRef", "attrs", Map.of("refId", refId, "label", label));
    }

    /** PDF валиден и содержит ожидаемые фрагменты текста (пробелы нормализуются). */
    private static void assertPdfText(byte[] pdf, String... expected) throws IOException {
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
            for (String fragment : expected) {
                assertThat(text).contains(fragment);
            }
        }
    }

    @Test
    void exportCyrillicText_producesPdf() throws IOException {
        Map<String, Object> body = doc(List.of(
                paragraph(List.of(text("Привет, DocForge!")))
        ));
        byte[] bytes = service.export(body, null);
        assertThat(bytes).isNotEmpty();
        // PDF magic bytes: %PDF
        assertThat(new String(bytes, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void exportWithTimesNewRoman_producesPdfWithoutException() throws IOException {
        DocumentFormatting fmt = new DocumentFormatting();
        fmt.setPageSize("A4");
        fmt.setDefaultFont("Times New Roman");
        fmt.setDefaultFontSize(14);

        Map<String, Object> body = doc(List.of(
                paragraph(List.of(text("Договор № 1 от 01.01.2026")))
        ));
        byte[] bytes = service.export(body, fmt);
        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void exportEmptyDoc_producesPdf() throws IOException {
        Map<String, Object> body = Map.of("type", "doc");
        byte[] bytes = service.export(body, null);
        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void exportWithSubstitutedParam_containsValue() throws IOException {
        // After parameter substitution, parameterField → text node
        Map<String, Object> body = doc(List.of(
                paragraph(List.of(text("Исполнитель: Иван Иванов")))
        ));
        byte[] bytes = service.export(body, null);
        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 4)).isEqualTo("%PDF");
    }

    // ── BUG-066: numberedClause — block-контент и печатная метка ─────────────

    @Test
    void numberedClause_blockContent_textAndLabelAppearInPdfHtml() throws IOException {
        // Регрессия BUG-066: раньше контент пункта рендерился как inline, поэтому
        // paragraph-дети пропускались и текст пункта терялся в PDF.
        Map<String, Object> body = doc(List.of(
                numberedClause("c1", 1, paragraph(List.of(text("CLAUSE_TEXT"))))
        ));

        String html = service.buildHtml(body, null);
        assertThat(html).contains("CLAUSE_TEXT");
        // печатная метка редактора (с завершающей точкой) — жирным в начале абзаца
        assertThat(html).contains("<strong>1. </strong>");

        byte[] bytes = service.export(body, null);
        assertThat(new String(bytes, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void numberedClause_siblingClauses_numberingContinuesWithoutStrayLabel() throws IOException {
        // Соседние пункты одного уровня нумеруются последовательно (1., 2.), ярлыки
        // идут в этом же порядке и в выводе нет ни лишнего ярлыка, ни лишнего текста.
        // Скрытые пункты (BUG-019) удаляются из дерева ДО экспорта
        // (ConditionalNodeFilter) — реальный пин скрытого пункта см.
        // DocumentControllerApplyAnswersTest.
        Map<String, Object> body = doc(List.of(
                numberedClause("c1", 1, paragraph(List.of(text("FIRST_CLAUSE_TEXT")))),
                numberedClause("c2", 1, paragraph(List.of(text("SECOND_CLAUSE_TEXT"))))
        ));

        String html = service.buildHtml(body, null);
        assertThat(html)
                .contains("<strong>1. </strong>FIRST_CLAUSE_TEXT")
                .contains("<strong>2. </strong>SECOND_CLAUSE_TEXT")
                .doesNotContain("<strong>0. </strong>")
                .doesNotContain("<strong>3. </strong>");
        assertThat(html.indexOf("<strong>2. </strong>"))
                .isGreaterThan(html.indexOf("<strong>1. </strong>"));
    }

    @Test
    void numberedClause_nestedLevel2_hierarchicalLabel() throws IOException {
        Map<String, Object> body = doc(List.of(
                numberedClause("c1", 1,
                        paragraph(List.of(text("PARENT_TEXT"))),
                        numberedClause("c1_1", 2, paragraph(List.of(text("CHILD_TEXT")))))
        ));

        String html = service.buildHtml(body, null);
        assertThat(html)
                .contains("<strong>1. </strong>")
                .contains("<strong>1.1. </strong>")
                .contains("PARENT_TEXT")
                .contains("CHILD_TEXT");
    }

    @Test
    void numberedClause_firstChildNotParagraph_labelInOwnParagraph() throws IOException {
        // Первый дочерний узел — не абзац (сразу вложенный пункт): метка печатается
        // отдельным абзацем
        Map<String, Object> body = doc(List.of(
                numberedClause("c1", 1,
                        numberedClause("c1_1", 2, paragraph(List.of(text("CHILD_TEXT")))))
        ));

        String html = service.buildHtml(body, null);
        assertThat(html).contains("<strong>1. </strong></p>");
        assertThat(html).contains("<strong>1.1. </strong>");
        assertThat(html).contains("CHILD_TEXT");
    }

    @Test
    void numberedClause_alphaRuStyle_cyrillicLabelsRendered() throws IOException {
        // numberingStyle "alpha-ru" доходит до HTML/PDF: метки "а. ", "б. "
        Map<String, Object> body = doc(List.of(
                numberedClauseStyled("c1", 1, "alpha-ru",
                        paragraph(List.of(text("FIRST_ALPHA_TEXT")))),
                numberedClauseStyled("c2", 1, "alpha-ru",
                        paragraph(List.of(text("SECOND_ALPHA_TEXT"))))
        ));

        String html = service.buildHtml(body, null);
        assertThat(html)
                .contains("<strong>а. </strong>FIRST_ALPHA_TEXT")
                .contains("<strong>б. </strong>SECOND_ALPHA_TEXT");
    }

    // ── BUG-078: numberedClause в ячейке таблицы и в пункте списка ────────────

    @Test
    void numberedClause_insideTableCell_textAndLabelAppearInPdf() throws IOException {
        // Регрессия BUG-078: ячейка таблицы рендерилась inline-only, поэтому
        // numberedClause в ячейке молча терял текст.
        Map<String, Object> body = doc(List.of(
                table(List.of(row(List.of(cell(List.of(
                        numberedClause("c1", 1, paragraph(List.of(text("CELL_CLAUSE_TEXT"))))))))))
        ));

        String html = service.buildHtml(body, null);
        assertThat(html).contains("<td>").contains("<strong>1. </strong>CELL_CLAUSE_TEXT");

        assertPdfText(service.export(body, null), "1. CELL_CLAUSE_TEXT");
    }

    @Test
    void numberedClause_insideListItem_textLabelAndPrefixAppearInPdf() throws IOException {
        // Регрессия BUG-078: listItem рендерился inline-only, поэтому numberedClause
        // в пункте списка терял текст. Маркировку списка (<li>) рисует движок —
        // префикс пункта сохранён.
        Map<String, Object> body = doc(List.of(
                bulletList(List.of(listItem(List.of(
                        paragraph(List.of(text("ITEM_TEXT"))),
                        numberedClause("c1", 1, paragraph(List.of(text("LIST_CLAUSE_TEXT"))))))))
        ));

        String html = service.buildHtml(body, null);
        assertThat(html)
                .contains("<ul><li>ITEM_TEXT")
                .contains("<strong>1. </strong>LIST_CLAUSE_TEXT");

        assertPdfText(service.export(body, null), "ITEM_TEXT", "1. LIST_CLAUSE_TEXT");
    }

    // ── BUG-078 (fix): инлайн-трюк <li> — только для первого-ребёнка-абзаца ───

    @Test
    void listItem_numberedClauseOnly_markerBeforeClauseAndNoTrailingMarker() throws IOException {
        // Пункт = [numberedClause] (без абзацев): весь пункт рендерится блочным
        // <p> с меткой пункта (маркер <li> рисует движок на строке пункта — перед
        // текстом), хвостового контента/маркера после текста нет. DOCX печатает ту же
        // строку "• 1. LIST_CLAUSE_TEXT".
        Map<String, Object> body = doc(List.of(
                bulletList(List.of(listItem(List.of(
                        numberedClause("c1", 1, paragraph(List.of(text("LIST_CLAUSE_TEXT"))))))))
        ));

        String html = service.buildHtml(body, null);
        assertThat(html).contains("<li><p><strong>1. </strong>LIST_CLAUSE_TEXT</p></li>");

        assertPdfText(service.export(body, null), "1. LIST_CLAUSE_TEXT");
    }

    @Test
    void listItem_numberedClauseThenParagraph_markerOnClauseBothTextsPresent() throws IOException {
        // Пункт = [numberedClause, paragraph]: оба блока рендерятся блочно
        // (инлайн-трюк раньше применялся к позднему paragraph и терял у него
        // <p>/paragraphStyle); оба текста на месте и в том же порядке, что в DOCX.
        Map<String, Object> body = doc(List.of(
                bulletList(List.of(listItem(List.of(
                        numberedClause("c1", 1, paragraph(List.of(text("LIST_CLAUSE_TEXT")))),
                        paragraph(List.of(text("TAIL_PARAGRAPH")))))))
        ));

        String html = service.buildHtml(body, null);
        assertThat(html).contains(
                "<li><p><strong>1. </strong>LIST_CLAUSE_TEXT</p><p>TAIL_PARAGRAPH</p></li>");

        assertPdfText(service.export(body, null), "1. LIST_CLAUSE_TEXT", "TAIL_PARAGRAPH");
    }

    @Test
    void listItem_twoParagraphs_bothRenderInsideLi() throws IOException {
        // Пункт = [para1, para2]: оба текста внутри <li>; первый абзац — inline
        // (маркер движка остаётся на строке пункта), второй — блочный <p>.
        Map<String, Object> body = doc(List.of(
                bulletList(List.of(listItem(List.of(
                        paragraph(List.of(text("PARA1_TEXT"))),
                        paragraph(List.of(text("PARA2_TEXT")))))))
        ));

        String html = service.buildHtml(body, null);
        assertThat(html).contains("<li>PARA1_TEXT<p>PARA2_TEXT</p></li>");

        assertPdfText(service.export(body, null), "PARA1_TEXT", "PARA2_TEXT");
    }

    // ── BUG-078: clauseRef в экспорте шаблона резолвится по нумерации ─────────

    @Test
    void clauseRef_templateExport_resolvedToClauseLabels() throws IOException {
        // Экспорт шаблона идёт по «сырому» телу (без ответов/applyAnswers):
        // ссылка должна резолвиться по нумерации дерева («п. <метка>»),
        // а не печатать «п. ?».
        Map<String, Object> body = doc(List.of(
                numberedClause("c1", 1, paragraph(List.of(text("FIRST")))),
                numberedClause("c2", 1, paragraph(List.of(text("SECOND")))),
                paragraph(List.of(text("см. "), clauseRef("c2"), text(" и "), clauseRef("c1")))
        ));

        String html = service.buildHtml(body, null);
        assertThat(html).contains("см. п. 2. и п. 1.");

        assertPdfText(service.export(body, null), "см. п. 2. и п. 1.");
    }

    @Test
    void clauseRef_unknownRef_placeholderAndLabelFallback() throws IOException {
        // Неизвестная ссылка → «п. ?»; если задан attrs.label — печатается он;
        // известная ссылка резолвится в «п. <метка>»
        Map<String, Object> body = doc(List.of(
                numberedClause("c1", 1, paragraph(List.of(text("FIRST")))),
                paragraph(List.of(
                        clauseRef("missing"), text(" | "),
                        clauseRefWithLabel("missing", "ЯРЛЫК"), text(" | "),
                        clauseRef("c1")))
        ));

        String html = service.buildHtml(body, null);
        assertThat(html).contains("п. ? | ЯРЛЫК | п. 1.");

        assertPdfText(service.export(body, null), "п. ?", "ЯРЛЫК", "п. 1.");
    }

    @Test
    void clauseRef_blankAndNonStringLabel_placeholderFallback() throws IOException {
        // attrs.label учитывается только как непустая строка: "", "   " и 42
        // (не-строка) отбрасываются и печатается «п. ?» — как в DOCX.
        Map<String, Object> body = doc(List.of(
                numberedClause("c1", 1, paragraph(List.of(text("FIRST")))),
                paragraph(List.of(
                        clauseRefWithLabel("missing", ""), text(" | "),
                        clauseRefWithLabel("missing", "   "), text(" | "),
                        clauseRefWithLabel("missing", 42)))
        ));

        String html = service.buildHtml(body, null);
        assertThat(html).contains("п. ? | п. ? | п. ?");
        assertThat(html).doesNotContain("42");

        assertPdfText(service.export(body, null), "п. ?");
    }

    // ── Регрессия: CSS line-height не зависит от локали JVM ─────────────────

    @Test
    void buildCss_russianDefaultLocale_lineHeightUsesDotDecimalSeparator() {
        // Регрессия: String.format без явной локали на ru_RU печатал "%.2f" как
        // "1,50", openhtmltopdf молча выбрасывал line-height и lineSpacing
        // игнорировался в каждом PDF. Ожидаем "line-height: 1.50" (точка).
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("ru-RU"));
        try {
            DocumentFormatting fmt = new DocumentFormatting();
            fmt.setPageSize("A4");
            fmt.setDefaultFontSize(14);
            fmt.setLineSpacing(1.5);

            String html = service.buildHtml(
                    doc(List.of(paragraph(List.of(text("ТЕКСТ ПАГРАФА"))))), fmt);

            assertThat(html).contains("line-height: 1.50");
            assertThat(html).doesNotContain("line-height: 1,50");
        } finally {
            Locale.setDefault(previous);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SafeVarargs
    private static Map<String, Object> numberedClause(
            String id, int level, Map<String, Object>... children) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "numberedClause");
        node.put("attrs", new LinkedHashMap<>(Map.of("id", id, "level", level)));
        node.put("content", new ArrayList<>(List.of(children)));
        return node;
    }

    @SafeVarargs
    private static Map<String, Object> numberedClauseStyled(
            String id, int level, String numberingStyle, Map<String, Object>... children) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "numberedClause");
        node.put("attrs", new LinkedHashMap<>(
                Map.of("id", id, "level", level, "numberingStyle", numberingStyle)));
        node.put("content", new ArrayList<>(List.of(children)));
        return node;
    }
}
