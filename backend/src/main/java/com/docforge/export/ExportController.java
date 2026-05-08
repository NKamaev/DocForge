package com.docforge.export;

import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.template.TemplateRepository;
import com.docforge.template.model.Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class ExportController {

    private final TemplateRepository templateRepository;
    private final DocxExportService docxExportService;
    private final PdfExportService pdfExportService;

    @PostMapping("/{id}/export/docx")
    public ResponseEntity<byte[]> exportDocx(@PathVariable UUID id) throws IOException {
        Template template = findOrThrow(id);
        byte[] bytes = docxExportService.export(template.getBody(), template.getFormatting());
        log.info("DOCX экспорт шаблона id={} size={} bytes", id, bytes.length);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"template-" + id + ".docx\"")
                .body(bytes);
    }

    @PostMapping("/{id}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID id) throws IOException {
        Template template = findOrThrow(id);
        byte[] bytes = pdfExportService.export(template.getBody(), template.getFormatting());
        log.info("PDF экспорт шаблона id={} size={} bytes", id, bytes.length);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"template-" + id + ".pdf\"")
                .body(bytes);
    }

    private Template findOrThrow(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Шаблон не найден: " + id));
    }
}
