package com.docforge.export;

import com.docforge.template.model.DocumentFormatting;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
}
