package com.docforge.export;

import com.docforge.template.model.DocumentFormatting;
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
