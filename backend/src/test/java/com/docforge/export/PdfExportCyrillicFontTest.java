package com.docforge.export;

import com.docforge.template.model.DocumentFormatting;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регрессия BUG-077: кириллица в PDF должна рендериться приложенным NotoSerif,
 * а не base-14 Times-Roman (у которого нет кириллических глифов — openhtmltopdf
 * рисует их символом '#').
 */
class PdfExportCyrillicFontTest {

    private static final String CYRILLIC = "Привет мир — кириллица";

    private final PdfExportServiceImpl service = new PdfExportServiceImpl();

    // ── BUG-077: кириллица и встроенный шрифт ────────────────────────────────

    @Test
    void cyrillic_nullFormatting_rendersWithEmbeddedNotoSerif() throws IOException {
        // Точное воспроизведение тикета: шаблон создан без formatting
        byte[] pdf = service.export(doc(List.of(paragraph(List.of(text(CYRILLIC))))), null);
        assertCyrillicRenderedWithEmbeddedFont(pdf, CYRILLIC);
    }

    @Test
    void cyrillic_defaultFontNull_rendersWithEmbeddedNotoSerif() throws IOException {
        DocumentFormatting fmt = new DocumentFormatting();
        fmt.setDefaultFontSize(14);
        byte[] pdf = service.export(doc(List.of(paragraph(List.of(text(CYRILLIC))))), fmt);
        assertCyrillicRenderedWithEmbeddedFont(pdf, CYRILLIC);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Noto Serif",
            "Times New Roman",
            "Times New Roman, serif",
            "Times",
            "serif",
            "Arial",          // неизвестное семейство → fallback на NotoSerif
            "",               // пустое значение → fallback на NotoSerif
            "\"Times New Roman\", serif, \"Noto Serif\""
    })
    void cyrillic_anyDefaultFont_rendersWithEmbeddedNotoSerif(String defaultFont) throws IOException {
        DocumentFormatting fmt = new DocumentFormatting();
        fmt.setDefaultFont(defaultFont);
        fmt.setDefaultFontSize(14);
        byte[] pdf = service.export(doc(List.of(paragraph(List.of(text(CYRILLIC))))), fmt);
        assertCyrillicRenderedWithEmbeddedFont(pdf, CYRILLIC);
    }

    // ── BUG-077: грани шрифта (bold/italic) ──────────────────────────────────

    @Test
    void boldItalicFaces_mapToNotoSerifFaces() throws IOException {
        Map<String, Object> body = doc(List.of(paragraph(List.of(
                markedText("Жирный", "bold"),
                text(" "),
                markedText("Курсив", "italic"),
                text(" "),
                markedText("ЖирКурс", "bold", "italic"),
                text(" "),
                text("Обычный")
        ))));

        byte[] pdf = service.export(body, null);
        assertCyrillicRenderedWithEmbeddedFont(pdf, "Жирный", "Курсив", "ЖирКурс", "Обычный");

        Set<String> fontNames = fontNames(pdf);
        assertThat(fontNames).anyMatch(n -> n.endsWith("NotoSerif-Regular"));
        assertThat(fontNames).anyMatch(n -> n.endsWith("NotoSerif-Bold"));
        assertThat(fontNames).anyMatch(n -> n.endsWith("NotoSerif-Italic"));
        assertThat(fontNames).anyMatch(n -> n.endsWith("NotoSerif-BoldItalic"));
    }

    @Test
    void numberedClause_boldLabelAndCyrillicText_rendered() throws IOException {
        // BUG-066 не должен сломаться, а жирная метка пункта — это NotoSerif-Bold
        Map<String, Object> clause = new LinkedHashMap<>();
        clause.put("type", "numberedClause");
        clause.put("attrs", new LinkedHashMap<>(Map.of("id", "c1", "level", 1, "numberingStyle", "alpha-ru")));
        clause.put("content", new ArrayList<>(List.of(paragraph(List.of(text("Первый пункт"))))));

        byte[] pdf = service.export(doc(List.of(clause)), null);
        assertCyrillicRenderedWithEmbeddedFont(pdf, "Первый пункт");
        assertThat(fontNames(pdf)).anyMatch(n -> n.endsWith("NotoSerif-Bold"));
    }

    // ── BUG-077: font-family из textStyle-меток не должен уводить в base-14 ─

    @ParameterizedTest
    @ValueSource(strings = {"Times New Roman", "times new roman", "serif", "Courier", "Arial"})
    void textStyleMark_anyFontFamily_cyrillicStillRendered(String family) throws IOException {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("fontFamily", family);
        Map<String, Object> mark = new LinkedHashMap<>();
        mark.put("type", "textStyle");
        mark.put("attrs", attrs);
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "text");
        node.put("text", "Стиль шрифта — ёж");
        node.put("marks", List.of(mark));

        byte[] pdf = service.export(doc(List.of(paragraph(List.of(node)))), null);
        assertCyrillicRenderedWithEmbeddedFont(pdf, "Стиль шрифта — ёж");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** PDF содержит точный кириллический текст и только встроенные шрифты с кириллицей. */
    private static void assertCyrillicRenderedWithEmbeddedFont(byte[] pdf, String... expected) throws IOException {
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            for (String fragment : expected) {
                assertThat(text).contains(fragment);
            }

            Set<String> names = new LinkedHashSet<>();
            for (PDPage page : doc.getPages()) {
                for (COSName cosName : page.getResources().getFontNames()) {
                    PDFont font = page.getResources().getFont(cosName);
                    // base-14 (PDType1Font) не встраивается и не имеет кириллицы
                    assertThat(font)
                            .as("base-14 font %s must not be used", font.getName())
                            .isNotInstanceOf(PDType1Font.class);
                    assertThat(font).isInstanceOf(PDType0Font.class);
                    assertThat(font.getFontDescriptor()).isNotNull();
                    // emb=yes: шрифт реально встроен в PDF
                    assertThat(font.getFontDescriptor().getFontFile2())
                            .as("font %s must be embedded", font.getName())
                            .isNotNull();
                    // uni=yes: есть ToUnicode → текст извлекается из PDF
                    assertThat(font.getCOSObject().getDictionaryObject(COSName.TO_UNICODE))
                            .as("font %s must have ToUnicode", font.getName())
                            .isNotNull();
                    assertThat(font.getName()).contains("NotoSerif");
                    names.add(font.getName());
                }
            }
            assertThat(names).isNotEmpty();
        }
    }

    private static Set<String> fontNames(byte[] pdf) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        try (PDDocument doc = PDDocument.load(pdf)) {
            for (PDPage page : doc.getPages()) {
                for (COSName cosName : page.getResources().getFontNames()) {
                    names.add(page.getResources().getFont(cosName).getName());
                }
            }
        }
        return names;
    }

    private static Map<String, Object> doc(List<Map<String, Object>> content) {
        return Map.of("type", "doc", "content", content);
    }

    private static Map<String, Object> paragraph(List<Map<String, Object>> inlines) {
        return Map.of("type", "paragraph", "content", inlines);
    }

    private static Map<String, Object> text(String value) {
        return Map.of("type", "text", "text", value);
    }

    private static Map<String, Object> markedText(String value, String... markTypes) {
        List<Map<String, Object>> marks = new ArrayList<>();
        for (String type : markTypes) marks.add(Map.of("type", type));
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "text");
        node.put("text", value);
        node.put("marks", marks);
        return node;
    }
}
