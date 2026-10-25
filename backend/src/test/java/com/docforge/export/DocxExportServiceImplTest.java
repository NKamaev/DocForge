package com.docforge.export;

import com.docforge.template.model.DocumentFormatting;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
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

    private static Map<String, Object> orderedList(List<Map<String, Object>> items) {
        return Map.of("type", "orderedList", "content", items);
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

    // ── BUG-078: numberedClause в ячейке таблицы и в пункте списка ────────────

    @Test
    void numberedClause_insideTableCell_textAndLabelAppearInDocx() throws IOException {
        // Регрессия BUG-078: ячейка таблицы рендерилась inline-only, поэтому
        // numberedClause в ячейке молча терял текст.
        Map<String, Object> body = doc(List.of(
                table(List.of(row(List.of(cell(List.of(
                        numberedClause("c1", 1, paragraph(List.of(text("CELL_CLAUSE_TEXT"))))))))))
        ));

        byte[] bytes = service.export(body, null);

        // word/document.xml: текст пункта и его печатная метка реально в файле
        assertThat(stripTags(wordDocumentXml(bytes))).contains("1. CELL_CLAUSE_TEXT");
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertThat(new XWPFWordExtractor(doc).getText()).contains("1. CELL_CLAUSE_TEXT");
        }
    }

    @Test
    void numberedClause_insideListItem_textLabelAndBulletAppearInDocx() throws IOException {
        // Регрессия BUG-078: listItem рендерил только paragraph-детей, поэтому
        // numberedClause в пункте списка терял текст. Маркер списка сохраняется.
        Map<String, Object> body = doc(List.of(
                bulletList(List.of(listItem(List.of(
                        paragraph(List.of(text("ITEM_TEXT"))),
                        numberedClause("c1", 1, paragraph(List.of(text("LIST_CLAUSE_TEXT"))))))))
        ));

        byte[] bytes = service.export(body, null);

        assertThat(wordDocumentXml(bytes)).contains("ITEM_TEXT").contains("LIST_CLAUSE_TEXT");
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String extracted = new XWPFWordExtractor(doc).getText();
            // буллит по-прежнему печатается перед первым абзацем пункта списка
            assertThat(extracted).contains("• ITEM_TEXT");
            assertThat(extracted).contains("1. LIST_CLAUSE_TEXT");
        }
    }

    @Test
    void orderedListItem_numberPrefixRendersBeforeFirstParagraph() throws IOException {
        Map<String, Object> body = doc(List.of(
                orderedList(List.of(
                        listItem(List.of(paragraph(List.of(text("ITEM_TEXT"))))),
                        listItem(List.of(paragraph(List.of(text("SECOND_ITEM")))))))
        ));

        byte[] bytes = service.export(body, null);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String extracted = new XWPFWordExtractor(doc).getText();
            assertThat(nonEmptyLines(extracted)).containsExactly("1. ITEM_TEXT", "2. SECOND_ITEM");
        }
    }

    // ── BUG-078 (fix): маркер списка ставится по первому дочернему узлу ───────

    @Test
    void listItem_numberedClauseOnly_markerBeforeClauseAndNoTrailingMarker() throws IOException {
        // Пункт = [numberedClause] (без абзацев): маркер списка печатается ПЕРЕД
        // текстом пункта ("• 1. " одной строкой с меткой пункта), а не отдельной
        // строкой-маркером после содержимого.
        Map<String, Object> body = doc(List.of(
                bulletList(List.of(listItem(List.of(
                        numberedClause("c1", 1, paragraph(List.of(text("LIST_CLAUSE_TEXT"))))))))
        ));

        byte[] bytes = service.export(body, null);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String extracted = new XWPFWordExtractor(doc).getText();
            // точная последовательность строк: ровно одна строка, маркер перед
            // текстом пункта, хвостовой строки-маркера нет
            assertThat(nonEmptyLines(extracted)).containsExactly("• 1. LIST_CLAUSE_TEXT");
        }
    }

    @Test
    void listItem_numberedClauseThenParagraph_markerOnClauseBothTextsPresent() throws IOException {
        // Пункт = [numberedClause, paragraph]: маркер + отступ идут в первый блок
        // пункта (метка numberedClause), а не в поздний paragraph; оба текста
        // на месте и в том же порядке, что в PDF.
        Map<String, Object> body = doc(List.of(
                bulletList(List.of(listItem(List.of(
                        numberedClause("c1", 1, paragraph(List.of(text("LIST_CLAUSE_TEXT")))),
                        paragraph(List.of(text("TAIL_PARAGRAPH")))))))
        ));

        byte[] bytes = service.export(body, null);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String extracted = new XWPFWordExtractor(doc).getText();
            assertThat(nonEmptyLines(extracted))
                    .containsExactly("• 1. LIST_CLAUSE_TEXT", "TAIL_PARAGRAPH");
        }
    }

    @Test
    void listItem_twoParagraphs_bothKeepListIndent() throws IOException {
        // Пункт = [para1, para2]: ОБА абзаца сохраняют отступ списка (0.5"),
        // второй абзац больше не теряет отступ, проходя через renderNode.
        Map<String, Object> body = doc(List.of(
                bulletList(List.of(listItem(List.of(
                        paragraph(List.of(text("PARA1_TEXT"))),
                        paragraph(List.of(text("PARA2_TEXT")))))))
        ));

        byte[] bytes = service.export(body, null);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String extracted = new XWPFWordExtractor(doc).getText();
            assertThat(nonEmptyLines(extracted)).containsExactly("• PARA1_TEXT", "PARA2_TEXT");
            assertThat(doc.getParagraphs()).hasSize(2);
            for (XWPFParagraph para : doc.getParagraphs()) {
                assertThat(para.getCTP().getPPr().getInd().getLeft().toString())
                        .as("отступ пункта списка (720 twips) у абзаца: %s", para.getText())
                        .isEqualTo("720");
            }
        }
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

        byte[] bytes = service.export(body, null);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertThat(new XWPFWordExtractor(doc).getText()).contains("см. п. 2. и п. 1.");
        }
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

        byte[] bytes = service.export(body, null);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertThat(new XWPFWordExtractor(doc).getText())
                    .contains("п. ? | ЯРЛЫК | п. 1.");
        }
    }

    @Test
    void clauseRef_blankAndNonStringLabel_placeholderFallback() throws IOException {
        // attrs.label учитывается только как непустая строка: "", "   " и 42
        // (не-строка) отбрасываются и печатается «п. ?» — как в PDF.
        Map<String, Object> body = doc(List.of(
                numberedClause("c1", 1, paragraph(List.of(text("FIRST")))),
                paragraph(List.of(
                        clauseRefWithLabel("missing", ""), text(" | "),
                        clauseRefWithLabel("missing", "   "), text(" | "),
                        clauseRefWithLabel("missing", 42)))
        ));

        byte[] bytes = service.export(body, null);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String extracted = new XWPFWordExtractor(doc).getText();
            assertThat(extracted).contains("п. ? | п. ? | п. ?");
            assertThat(extracted).doesNotContain("42");
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

    /** Текст word/document.xml без тегов — фактическое содержимое файла. */
    private static String stripTags(String xml) {
        return xml.replaceAll("<[^>]*>", "");
    }
}
