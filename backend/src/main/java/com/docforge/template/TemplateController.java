package com.docforge.template;

import com.docforge.common.exception.ResourceNotFoundException;
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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class TemplateController {

    /** Роль администратора (см. {@code UserDetailsServiceImpl}). */
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final TemplateService templateService;
    private final TemplateOwnerResolver ownerResolver;

    @GetMapping
    public ResponseEntity<Page<TemplateListItemResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean published,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdAfter,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(templateService.listVisible(
                page, size, category, published, createdAfter, userId, isAdmin(userDetails)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(templateService.getReadable(id, userId, isAdmin(userDetails)));
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
    public ResponseEntity<List<TemplateVersionDTO>> listVersions(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(templateService.listVersionsVisible(id, userId, isAdmin(userDetails)));
    }

    @GetMapping("/{id}/versions/{versionNumber}")
    public ResponseEntity<TemplateSnapshot> getVersion(
            @PathVariable UUID id,
            @PathVariable int versionNumber,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(templateService.getSnapshotVisible(
                id, versionNumber, userId, isAdmin(userDetails)));
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
            @RequestParam int to,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(templateService.getDiffVisible(
                id, from, to, userId, isAdmin(userDetails)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Признак администратора (см. {@code UserDetailsServiceImpl}: ROLE_ADMIN). */
    private boolean isAdmin(UserDetails userDetails) {
        return userDetails != null && userDetails.getAuthorities().stream()
                .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
    }

    /**
     * UUID вызывающего по логину. Если пользователя в {@code users} нет —
     * {@code null}: вызывающий трактуется как «не владелец и не админ»,
     * то есть видит только опубликованные шаблоны (BUG-059).
     */
    private UUID resolveUserId(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null) {
            return null;
        }
        try {
            return ownerResolver.resolve(userDetails.getUsername());
        } catch (ResourceNotFoundException e) {
            log.warn("Пользователь '{}' не найден: {}", userDetails.getUsername(), e.getMessage());
            return null;
        }
    }
}
