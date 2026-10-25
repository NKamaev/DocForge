package com.docforge.export;

import com.docforge.template.model.DocumentFormatting;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocxExportServiceImplTest {

    private final DocxExportService service = new DocxExportServiceImpl();

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
    void exportPlainText_producesDocxWithContent() throws IOException {
        Map<String, Object> body = doc(List.of(
                paragraph(List.of(text("Привет, DocForge!")))
        ));
        byte[] bytes = service.export(body, null);
        assertThat(bytes).isNotEmpty();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            XWPFWordExtractor ex = new XWPFWordExtractor(doc);
            assertThat(ex.getText()).contains("Привет, DocForge!");
        }
    }

    @Test
    void parameterField_substituteValue_appearsInDocx() throws IOException {
        // Simulates body where parameterField was already substituted to text
        Map<String, Object> body = doc(List.of(
                paragraph(List.of(text("Договор с Иван Иванович")))
        ));
        byte[] bytes = service.export(body, null);
        assertThat(bytes).isNotEmpty();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            XWPFWordExtractor ex = new XWPFWordExtractor(doc);
            assertThat(ex.getText()).contains("Иван Иванович");
        }
    }

    @Test
    void parameterField_notSubstituted_rendersPlaceholder() throws IOException {
        // When parameterField nodes remain in body, they render as {{display}}
        Map<String, Object> attrs = Map.of("parameterCode", "name", "display", "ФИО");
        Map<String, Object> paramNode = Map.of("type", "parameterField", "attrs", attrs);
        Map<String, Object> body = doc(List.of(paragraph(List.of(paramNode))));

        byte[] bytes = service.export(body, null);
        assertThat(bytes).isNotEmpty();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            XWPFWordExtractor ex = new XWPFWordExtractor(doc);
            assertThat(ex.getText()).contains("{{ФИО}}");
        }
    }

    @Test
    void exportWithFormatting_doesNotThrow() throws IOException {
        DocumentFormatting fmt = new DocumentFormatting();
        fmt.setPageSize("A4");
        fmt.setDefaultFont("Times New Roman");
        fmt.setDefaultFontSize(14);
        Map<String, Object> body = doc(List.of(paragraph(List.of(text("Текст")))));
        byte[] bytes = service.export(body, fmt);
        assertThat(bytes).isNotEmpty();
    }

    // ── BUG-066: numberedClause — block-контент и печатная метка ─────────────

    @Test
    void numberedClause_blockContent_textAndLabelAppearInDocx() throws IOException {
        // Регрессия BUG-066: раньше контент пункта рендерился как inline, поэтому
        // paragraph-дети пропускались и текст пункта терялся в DOCX.
        Map<String, Object> body = doc(List.of(
                numberedClause("c1", 1, paragraph(List.of(text("CLAUSE_TEXT"))))
        ));

        byte[] bytes = service.export(body, null);

        // word/document.xml: текст пункта реально присутствует в файле
        assertThat(wordDocumentXml(bytes)).contains("CLAUSE_TEXT");
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String extracted = new XWPFWordExtractor(doc).getText();
            // печатная метка редактора (с завершающей точкой) + пробел + текст
            assertThat(extracted).contains("1. CLAUSE_TEXT");
        }
    }

    @Test
    void numberedClause_siblingClauses_numberingContinuesWithoutStrayLabel() throws IOException {
        // Соседние пункты одного уровня нумеруются последовательно (1., 2.), а в
        // выводе нет ни лишнего ярлыка, ни лишнего текста (точная последовательность
        // строк абзацев). Скрытые пункты (BUG-019) удаляются из дерева ДО экспорта
        // (ConditionalNodeFilter) — реальный пин скрытого пункта см.
        // DocumentControllerApplyAnswersTest.
        Map<String, Object> body = doc(List.of(
                numberedClause("c1", 1, paragraph(List.of(text("FIRST_CLAUSE_TEXT")))),
                numberedClause("c2", 1, paragraph(List.of(text("SECOND_CLAUSE_TEXT"))))
        ));

        byte[] bytes = service.export(body, null);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String extracted = new XWPFWordExtractor(doc).getText();
            assertThat(nonEmptyLines(extracted))
                    .containsExactly("1. FIRST_CLAUSE_TEXT", "2. SECOND_CLAUSE_TEXT");
        }
    }

    @Test
    void numberedClause_nestedLevel2_hierarchicalLabel() throws IOException {
        Map<String, Object> body = doc(List.of(
                numberedClause("c1", 1,
                        paragraph(List.of(text("PARENT_TEXT"))),
                        numberedClause("c1_1", 2, paragraph(List.of(text("CHILD_TEXT")))))
        ));

        byte[] bytes = service.export(body, null);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String extracted = new XWPFWordExtractor(doc).getText();
            assertThat(extracted)
                    .contains("1. PARENT_TEXT")
                    .contains("1.1. CHILD_TEXT");
        }
    }

    @Test
    void numberedClause_firstChildNotParagraph_labelInOwnParagraph() throws IOException {
        // Первый дочерний узел — не абзац (сразу вложенный пункт): метка печатается
        // отдельным абзацем
        Map<String, Object> body = doc(List.of(
                numberedClause("c1", 1,
                        numberedClause("c1_1", 2, paragraph(List.of(text("CHILD_TEXT")))))
        ));

        byte[] bytes = service.export(body, null);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String extracted = new XWPFWordExtractor(doc).getText();
            // точная последовательность абзацев: метка верхнего пункта отдельным
            // абзацем ("1."), затем абзац вложенного пункта ("1.1. CHILD_TEXT")
            assertThat(nonEmptyLines(extracted)).containsExactly("1.", "1.1. CHILD_TEXT");
        }
    }

    @Test
    void numberedClause_alphaRuStyle_cyrillicLabelsRendered() throws IOException {
        // numberingStyle "alpha-ru" доходит до DOCX: метки "а. ", "б. "
        Map<String, Object> body = doc(List.of(
                numberedClauseStyled("c1", 1, "alpha-ru",
                        paragraph(List.of(text("FIRST_ALPHA_TEXT")))),
                numberedClauseStyled("c2", 1, "alpha-ru",
                        paragraph(List.of(text("SECOND_ALPHA_TEXT"))))
        ));

        byte[] bytes = service.export(body, null);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String extracted = new XWPFWordExtractor(doc).getText();
            assertThat(nonEmptyLines(extracted))
                    .containsExactly("а. FIRST_ALPHA_TEXT", "б. SECOND_ALPHA_TEXT");
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

    /** Непустые строки извлечённого текста (по абзацам DOCX), в порядке следования. */
    private static List<String> nonEmptyLines(String extracted) {
        return extracted.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    /** Содержимое word/document.xml из DOCX-архива. */
    private static String wordDocumentXml(byte[] docxBytes) throws IOException {
        try (java.util.zip.ZipInputStream zip =
                     new java.util.zip.ZipInputStream(new ByteArrayInputStream(docxBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("word/document.xml not found in DOCX archive");
    }
}
