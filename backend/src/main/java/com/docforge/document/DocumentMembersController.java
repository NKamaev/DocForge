package com.docforge.document;

import com.docforge.document.dto.DocumentMemberDTO;
import com.docforge.document.dto.GrantRoleRequest;
import com.docforge.document.dto.UpdateRoleRequest;
import com.docforge.document.model.DocumentPermission;
import com.docforge.document.model.DocumentRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents/{documentId}/members")
@RequiredArgsConstructor
public class DocumentMembersController {

    private final DocumentPermissionService documentPermissionService;
    private final DocumentOwnerResolver ownerResolver;

    @GetMapping
    public ResponseEntity<List<DocumentMemberDTO>> list(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(documentId, userId, DocumentPermission.VIEW))
            throw new AccessDeniedException("Нет доступа к документу");
        return ResponseEntity.ok(documentPermissionService.listMembers(documentId, userId));
    }

    @PostMapping
    public ResponseEntity<DocumentMemberDTO> add(
            @PathVariable UUID documentId,
            @Valid @RequestBody GrantRoleRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(documentId, userId, DocumentPermission.SHARE))
            throw new AccessDeniedException("Нет прав на управление участниками документа");

        DocumentRole targetRole = parseDocumentRole(request.role());
        DocumentRole callerRole = documentPermissionService.getEffectiveRole(documentId, userId);
        if (targetRole == DocumentRole.OWNER && callerRole != DocumentRole.OWNER)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Нельзя назначить роль OWNER");

        documentPermissionService.grantRole(documentId, request.userId(), targetRole, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                documentPermissionService.listMembers(documentId, userId).stream()
                        .filter(m -> m.userId().equals(request.userId()))
                        .findFirst()
                        .orElseThrow()
        );
    }

    @PutMapping("/{targetUserId}")
    public ResponseEntity<DocumentMemberDTO> update(
            @PathVariable UUID documentId,
            @PathVariable UUID targetUserId,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(documentId, userId, DocumentPermission.SHARE))
            throw new AccessDeniedException("Нет прав на управление участниками документа");

        DocumentRole currentRole = documentPermissionService.getEffectiveRole(documentId, targetUserId);
        if (currentRole == DocumentRole.OWNER)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Нельзя изменить роль OWNER");

        DocumentRole newRole = parseDocumentRole(request.role());
        documentPermissionService.grantRole(documentId, targetUserId, newRole, userId);

        return ResponseEntity.ok(
                documentPermissionService.listMembers(documentId, userId).stream()
                        .filter(m -> m.userId().equals(targetUserId))
                        .findFirst()
                        .orElseThrow()
        );
    }

    @DeleteMapping("/{targetUserId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID documentId,
            @PathVariable UUID targetUserId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!documentPermissionService.hasPermission(documentId, userId, DocumentPermission.SHARE))
            throw new AccessDeniedException("Нет прав на управление участниками документа");

        DocumentRole targetRole = documentPermissionService.getEffectiveRole(documentId, targetUserId);
        if (targetRole == DocumentRole.OWNER)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Нельзя удалить OWNER из участников");

        documentPermissionService.revokeRole(documentId, targetUserId, userId);
        return ResponseEntity.noContent().build();
    }

    private DocumentRole parseDocumentRole(String role) {
        try {
            return DocumentRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестная роль: " + role);
        }
    }
}
