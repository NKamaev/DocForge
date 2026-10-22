package com.docforge.template;

import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.DiffResult;
import com.docforge.template.dto.RollbackResponse;
import com.docforge.template.dto.SaveVersionRequest;
import com.docforge.template.dto.SaveVersionResponse;
import com.docforge.template.dto.ScriptTestRequest;
import com.docforge.template.dto.ScriptTestResult;
import com.docforge.template.dto.TemplateListItemResponse;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.dto.TemplateVersionDTO;
import com.docforge.template.dto.UpdateTemplateRequest;
import com.docforge.template.model.TemplateSnapshot;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final TemplateOwnerResolver ownerResolver;

    @GetMapping
    public ResponseEntity<Page<TemplateListItemResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean published,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdAfter
    ) {
        return ResponseEntity.ok(templateService.list(page, size, category, published, createdAfter));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(templateService.getById(id));
    }

    @PostMapping
    public ResponseEntity<TemplateResponse> create(
            @Valid @RequestBody CreateTemplateRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID ownerId = ownerResolver.resolve(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.create(ownerId, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TemplateResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTemplateRequest request
    ) {
        return ResponseEntity.ok(templateService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<TemplateResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(templateService.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<TemplateResponse> unpublish(@PathVariable UUID id) {
        return ResponseEntity.ok(templateService.unpublish(id));
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<TemplateValidationResult> validate(@PathVariable UUID id) {
        return ResponseEntity.ok(templateService.validate(id));
    }

    @PostMapping("/{id}/scripts/test")
    public ResponseEntity<ScriptTestResult> testScript(
            @PathVariable UUID id,
            @Valid @RequestBody ScriptTestRequest request
    ) {
        return ResponseEntity.ok(templateService.testScript(id, request));
    }

    // ── Versioning ────────────────────────────────────────────────────────────

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<TemplateVersionDTO>> listVersions(@PathVariable UUID id) {
        return ResponseEntity.ok(templateService.listVersions(id));
    }

    @GetMapping("/{id}/versions/{versionNumber}")
    public ResponseEntity<TemplateSnapshot> getVersion(
            @PathVariable UUID id,
            @PathVariable int versionNumber
    ) {
        return ResponseEntity.ok(templateService.getSnapshot(id, versionNumber));
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<SaveVersionResponse> saveVersion(
            @PathVariable UUID id,
            @Valid @RequestBody SaveVersionRequest request
    ) {
        return ResponseEntity.ok(templateService.saveVersion(id, request));
    }

    @PostMapping("/{id}/rollback/{versionNumber}")
    public ResponseEntity<RollbackResponse> rollback(
            @PathVariable UUID id,
            @PathVariable int versionNumber
    ) {
        return ResponseEntity.ok(templateService.rollback(id, versionNumber));
    }

    @GetMapping("/{id}/versions/diff")
    public ResponseEntity<DiffResult> getDiff(
            @PathVariable UUID id,
            @RequestParam int from,
            @RequestParam int to
    ) {
        return ResponseEntity.ok(templateService.getDiff(id, from, to));
    }
}
