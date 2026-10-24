package com.docforge.export;

import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.template.TemplateService;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.user.UserService;
import com.docforge.user.dto.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class ExportController {

    private final TemplateService templateService;
    private final UserService userService;
    private final DocxExportService docxExportService;
    private final PdfExportService pdfExportService;

    @PostMapping("/{id}/export/docx")
    public ResponseEntity<byte[]> exportDocx(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) throws IOException {
        TemplateResponse template = readableTemplate(id, userDetails);
        byte[] bytes = docxExportService.export(template.body(), template.formatting());
        log.info("DOCX экспорт шаблона id={} size={} bytes", id, bytes.length);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"template-" + id + ".docx\"")
                .body(bytes);
    }

    @PostMapping("/{id}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) throws IOException {
        TemplateResponse template = readableTemplate(id, userDetails);
        byte[] bytes = pdfExportService.export(template.body(), template.formatting());
        log.info("PDF экспорт шаблона id={} size={} bytes", id, bytes.length);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"template-" + id + ".pdf\"")
                .body(bytes);
    }

    /**
     * Шаблон, доступный вызывающему: опубликованный — всем, черновик — только
     * владельцу и администратору (BUG-059).
     *
     * <p>Раньше контроллер читал {@code TemplateRepository} напрямую и отдавал
     * содержимое любого шаблона любому аутентифицированному пользователю.
     */
    private TemplateResponse readableTemplate(UUID id, UserDetails userDetails) {
        UUID userId = null;
        boolean admin = false;
        if (userDetails != null && userDetails.getUsername() != null) {
            try {
                UserDTO user = userService.getCurrentUser(userDetails.getUsername());
                userId = user.id();
                admin = user.admin();
            } catch (ResourceNotFoundException e) {
                log.warn("Пользователь '{}' не найден: {}", userDetails.getUsername(), e.getMessage());
            }
        }
        return templateService.getReadable(id, userId, admin);
    }
}
