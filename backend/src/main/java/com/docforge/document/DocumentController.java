package com.docforge.document;

import com.docforge.document.dto.*;
import com.docforge.document.dto.MoveFolderRequest;
import com.docforge.document.generator.ConditionalNodeFilter;
import com.docforge.document.generator.ParameterSubstitutor;
import com.docforge.document.generator.TipTapTreeWalker;
import com.docforge.document.model.DocumentPermission;
import com.docforge.document.model.DocumentRole;
import com.docforge.document.model.DocumentRevisionBody;
import com.docforge.document.model.FolderPermission;
import com.docforge.export.DocxExportService;
import com.docforge.export.NodeResolver;
import com.docforge.export.PdfExportService;
import com.docforge.template.TemplateService;
import com.docforge.template.model.DocumentFormatting;
import com.docforge.template.model.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentPermissionService documentPermissionService;
    private final FolderPermissionService folderPermissionService;
    private final DocumentOwnerResolver ownerResolver;
    private final DocxExportService docxExportService;
    private final PdfExportService pdfExportService;
    private final TemplateService templateService;
    private final TipTapTreeWalker treeWalker;
    private final ConditionalNodeFilter conditionalNodeFilter;
    private final ParameterSubstitutor substitutor;
    private final NodeResolver nodeResolver;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    /** GET /api/documents — мои документы (пагинация) */
    @GetMapping
    public ResponseEntity<Page<DocumentListItemResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID ownerId = ownerResolver.resolve(userDetails.getUsername());
        return ResponseEntity.ok(documentService.list(ownerId, page, size));
    }

    /** POST /api/documents — создать документ из шаблона */
    @PostMapping
    public ResponseEntity<DocumentResponse> create(
            @Valid @RequestBody CreateDocumentRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID ownerId = ownerResolver.resolve(userDetails.getUsername());
        DocumentResponse response = documentService.create(
                request.templateId(), request.name(), ownerId, request.folderId(),
                request.templateVersionNumber());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** GET /api/documents/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, userId, DocumentPermission.READ))
            throw new AccessDeniedException("Нет доступа к документу");
        return ResponseEntity.ok(documentService.getById(id));
    }

    /** PATCH /api/documents/{id}/folder — переместить документ в папку (folderId = null — убрать) */
    @PatchMapping("/{id}/folder")
    public ResponseEntity<Void> moveToFolder(
            @PathVariable UUID id,
            @RequestBody MoveFolderRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, userId, DocumentPermission.MANAGE))
            throw new AccessDeniedException("Нет прав на перемещение документа");
        if (request.folderId() != null
                && !folderPermissionService.hasPermission(request.folderId(), userId, FolderPermission.WRITE))
            throw new AccessDeniedException("Нет прав на папку-цель");
        documentService.moveToFolder(id, request.folderId());
        return ResponseEntity.noContent().build();
    }

    /** DELETE /api/documents/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, userId, DocumentPermission.DELETE))
            throw new AccessDeniedException("Нет прав на удаление документа");
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Опросный лист ─────────────────────────────────────────────────────────

    /** GET /api/documents/{id}/questionnaire — текущее состояние с видимостью */
    @GetMapping("/{id}/questionnaire")
    public ResponseEntity<QuestionnaireResponse> getQuestionnaire(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, userId, DocumentPermission.READ))
            throw new AccessDeniedException("Нет доступа к документу");
        return ResponseEntity.ok(documentService.getQuestionnaire(id));
    }

    /** PUT /api/documents/{id}/questionnaire/answers — сохранить ответы */
    @PutMapping("/{id}/questionnaire/answers")
    public ResponseEntity<SaveAnswersResponse> saveAnswers(
            @PathVariable UUID id,
            @Valid @RequestBody SaveAnswersRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, userId, DocumentPermission.WRITE))
            throw new AccessDeniedException("Нет прав на редактирование документа");
        return ResponseEntity.ok(
                documentService.saveAnswers(id, request.answers()));
    }

    // ── Генерация ─────────────────────────────────────────────────────────────

    /** POST /api/documents/{id}/generate */
    @PostMapping("/{id}/generate")
    public ResponseEntity<GenerateResponse> generate(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, userId, DocumentPermission.WRITE))
            throw new AccessDeniedException("Нет прав на редактирование документа");
        return ResponseEntity.ok(documentService.generate(id));
    }

    /** PUT /api/documents/{id}/body — сохранить отредактированное тело */
    @PutMapping("/{id}/body")
    public ResponseEntity<Void> updateBody(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBodyRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, userId, DocumentPermission.WRITE))
            throw new AccessDeniedException("Нет прав на редактирование документа");
        documentService.updateBody(id, request.currentBody());
        return ResponseEntity.noContent().build();
    }

    // ── Экспорт ───────────────────────────────────────────────────────────────

    /** GET /api/documents/{id}/export/docx */
    @GetMapping("/{id}/export/docx")
    public ResponseEntity<byte[]> exportDocx(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, userId, DocumentPermission.READ))
            throw new AccessDeniedException("Нет доступа к документу");
        DocumentResponse doc = documentService.getById(id);
        Object body = doc.currentBody() != null ? doc.currentBody() : doc.generatedBody();
        if (body == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        }
        try {
            DocumentFormatting formatting = resolveFormatting(doc);
            @SuppressWarnings("unchecked")
            Map<String, Object> bodyMap = (Map<String, Object>) body;
            Map<String, Object> resolved = applyAnswers(bodyMap, doc.answers(), resolveParamIndex(doc));
            byte[] bytes = docxExportService.export(resolved, formatting);
            String filename = sanitizeFilename(doc.name()) + ".docx";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(filename).build().toString())
                    .body(bytes);
        } catch (IOException e) {
            log.error("DOCX export failed for document {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** GET /api/documents/{id}/export/pdf */
    @GetMapping("/{id}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, userId, DocumentPermission.READ))
            throw new AccessDeniedException("Нет доступа к документу");
        DocumentResponse doc = documentService.getById(id);
        Object body = doc.currentBody() != null ? doc.currentBody() : doc.generatedBody();
        if (body == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        }
        try {
            DocumentFormatting formatting = resolveFormatting(doc);
            @SuppressWarnings("unchecked")
            Map<String, Object> bodyMap = (Map<String, Object>) body;
            Map<String, Object> resolved = applyAnswers(bodyMap, doc.answers(), resolveParamIndex(doc));
            byte[] bytes = pdfExportService.export(resolved, formatting);
            String filename = sanitizeFilename(doc.name()) + ".pdf";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(filename).build().toString())
                    .body(bytes);
        } catch (IOException e) {
            log.error("PDF export failed for document {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ── Editions ──────────────────────────────────────────────────────────────

    /** GET /api/documents/{id}/revisions */
    @GetMapping("/{id}/revisions")
    public ResponseEntity<List<DocumentRevisionDTO>> listRevisions(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, userId, DocumentPermission.READ))
            throw new AccessDeniedException("Нет доступа к документу");
        return ResponseEntity.ok(documentService.listRevisions(id));
    }

    /** GET /api/documents/{id}/revisions/{revNum} */
    @GetMapping("/{id}/revisions/{revNum}")
    public ResponseEntity<DocumentRevisionBody> getRevision(
            @PathVariable UUID id,
            @PathVariable int revNum,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, userId, DocumentPermission.READ))
            throw new AccessDeniedException("Нет доступа к документу");
        return ResponseEntity.ok(documentService.getRevisionBody(id, revNum));
    }

    /** POST /api/documents/{id}/revisions — сохранить редакцию вручную */
    @PostMapping("/{id}/revisions")
    public ResponseEntity<DocumentRevisionDTO> saveRevision(
            @PathVariable UUID id,
            @RequestParam(required = false) String comment,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, userId, DocumentPermission.WRITE))
            throw new AccessDeniedException("Нет прав на редактирование документа");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.saveRevision(id, comment));
    }

    /** POST /api/documents/{id}/revisions/{revNum}/restore */
    @PostMapping("/{id}/revisions/{revNum}/restore")
    public ResponseEntity<DocumentRevisionDTO> restoreRevision(
            @PathVariable UUID id,
            @PathVariable int revNum,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, userId, DocumentPermission.WRITE))
            throw new AccessDeniedException("Нет прав на редактирование документа");
        return ResponseEntity.ok(documentService.restoreRevision(id, revNum));
    }

    /** GET /api/documents/{id}/revisions/{revNum}/diff — diff с предыдущей или указанной редакцией */
    @GetMapping("/{id}/revisions/{revNum}/diff")
    public ResponseEntity<RevisionDiffResponse> getRevisionDiff(
            @PathVariable UUID id,
            @PathVariable int revNum,
            @RequestParam(required = false) Integer compareTo,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, userId, DocumentPermission.READ))
            throw new AccessDeniedException("Нет доступа к документу");
        return ResponseEntity.ok(documentService.computeRevisionDiff(id, revNum, compareTo));
    }

    // ── Participants ──────────────────────────────────────────────────────────

    /** GET /api/documents/{id}/participants */
    @GetMapping("/{id}/participants")
    public ResponseEntity<List<DocumentParticipantDTO>> getParticipants(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, userId, DocumentPermission.READ))
            throw new AccessDeniedException("Нет доступа к документу");
        return ResponseEntity.ok(documentService.getParticipants(id));
    }

    /**
     * POST /api/documents/{id}/participants
     *
     * @deprecated используйте канонический {@code POST /api/documents/{id}/members}.
     */
    @Deprecated
    @PostMapping("/{id}/participants")
    public ResponseEntity<DocumentParticipantDTO> addParticipant(
            @PathVariable UUID id,
            @Valid @RequestBody AddParticipantRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, userId, DocumentPermission.SHARE))
            throw new AccessDeniedException("Нет прав на управление участниками");

        DocumentRole targetRole = parseDocumentRole(request.role());
        DocumentRole callerRole = documentPermissionService.getEffectiveRole(id, userId);
        if (targetRole == DocumentRole.OWNER && callerRole != DocumentRole.OWNER)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Нельзя назначить роль OWNER");

        DocumentParticipantDTO result = documentService.addParticipant(
                id, request.userId(), targetRole.name(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * DELETE /api/documents/{id}/participants/{userId}
     *
     * @deprecated используйте канонический {@code DELETE /api/documents/{id}/members/{userId}}.
     */
    @Deprecated
    @DeleteMapping("/{id}/participants/{userId}")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID currentUserId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(id, currentUserId, DocumentPermission.SHARE))
            throw new AccessDeniedException("Нет прав на управление участниками");

        DocumentRole targetRole = documentPermissionService.getEffectiveRole(id, userId);
        if (targetRole == DocumentRole.OWNER)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Нельзя удалить OWNER из участников");

        documentService.removeParticipant(id, userId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DocumentRole parseDocumentRole(String role) {
        try {
            return DocumentRole.valueOf(role != null ? role.toUpperCase() : "EDITOR");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестная роль: " + role + "; допустимо: VIEWER, EDITOR, MANAGER, OWNER");
        }
    }

    /**
     * Walks the TipTap tree and resolves inline nodes that require runtime context:
     * <ul>
     *   <li>{@code parameterField} — replaced with formatted answer text.</li>
     *   <li>{@code templateParam} — replaced with the raw answer value for {@code fieldId}.</li>
     *   <li>{@code clauseRef} — replaced with "п. X.Y" computed from the document tree.</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> applyAnswers(
            Map<String, Object> body,
            Map<String, Object> answers,
            Map<String, Parameter> paramIndex) {
        if (body == null) return body;
        Map<String, Object> safeAnswers = answers != null ? answers : Map.of();

        // Сначала убираем контент, скрытый условиями (иначе он попадёт в файл),
        // и только потом считаем нумерацию пунктов — скрытые пункты не должны
        // участвовать в счётчиках.
        Map<String, Object> visibleBody = conditionalNodeFilter.filter(body, safeAnswers);
        Map<String, String> clauseNumbering = nodeResolver.buildClauseNumberingMap(visibleBody);

        return treeWalker.transform(visibleBody, node -> {
            // null-safe: узел без "type" (например, после Yjs-правок) должен
            // попадать в default, а не ронять экспорт NPE
            return switch (String.valueOf(node.get("type"))) {
                case "parameterField" -> {
                    Map<String, Object> attrs = (Map<String, Object>) node.get("attrs");
                    String code = attrs != null ? (String) attrs.get("parameterCode") : null;
                    Parameter param = code != null ? paramIndex.get(code) : null;
                    Object value = code != null ? safeAnswers.get(code) : null;
                    String text = substitutor.format(value, param);
                    Map<String, Object> textNode = new LinkedHashMap<>();
                    textNode.put("type", "text");
                    textNode.put("text", text);
                    yield List.of(textNode);
                }
                case "templateParam" ->
                    List.of(nodeResolver.resolveTemplateParam(node, safeAnswers));
                case "clauseRef" ->
                    List.of(nodeResolver.resolveClauseRef(node, clauseNumbering));
                default -> List.of(node);
            };
        });
    }

    /** Loads template parameters from snapshot (preferred) or live template. */
    private Map<String, Parameter> resolveParamIndex(DocumentResponse doc) {
        List<Parameter> params = null;
        if (doc.templateSnapshotId() != null) {
            try {
                params = templateService.getSnapshotById(doc.templateSnapshotId()).getParameters();
            } catch (Exception e) {
                log.warn("Could not load snapshot parameters for document {} — falling back to live template", doc.id());
            }
        }
        if (params == null) {
            try {
                params = templateService.getById(doc.templateId()).parameters();
            } catch (Exception e) {
                log.warn("Could not load template parameters for document {} — using empty index", doc.id());
                return Map.of();
            }
        }
        if (params == null) return Map.of();
        return params.stream().collect(Collectors.toMap(Parameter::getCode, p -> p, (a, b) -> a));
    }

    private DocumentFormatting resolveFormatting(DocumentResponse doc) {
        // Try snapshot first (immutable), fall back to live template
        if (doc.templateSnapshotId() != null) {
            try {
                return templateService.getSnapshotById(doc.templateSnapshotId()).getFormatting();
            } catch (Exception e) {
                log.warn("Could not load snapshot formatting for document {} — falling back to live template", doc.id());
            }
        }
        try {
            return templateService.getById(doc.templateId()).formatting();
        } catch (Exception e) {
            log.warn("Could not load template formatting for document {} — using defaults", doc.id());
            return null;
        }
    }

    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return "document";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
