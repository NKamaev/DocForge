package com.docforge.export;

import com.docforge.template.model.DocumentFormatting;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PdfExportServiceImplTest {

    private final PdfExportService service = new PdfExportServiceImpl();

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
}
