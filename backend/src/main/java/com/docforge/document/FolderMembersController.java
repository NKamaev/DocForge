package com.docforge.document;

import com.docforge.document.dto.FolderMemberDTO;
import com.docforge.document.dto.GrantRoleRequest;
import com.docforge.document.dto.UpdateRoleRequest;
import com.docforge.document.model.FolderPermission;
import com.docforge.document.model.FolderRole;
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
@RequestMapping("/api/folders/{folderId}/members")
@RequiredArgsConstructor
public class FolderMembersController {

    private final FolderPermissionService folderPermissionService;
    private final DocumentOwnerResolver ownerResolver;

    @GetMapping
    public ResponseEntity<List<FolderMemberDTO>> list(
            @PathVariable UUID folderId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!folderPermissionService.hasPermission(folderId, userId, FolderPermission.VIEW))
            throw new AccessDeniedException("Нет доступа к папке");
        return ResponseEntity.ok(folderPermissionService.listMembers(folderId, userId));
    }

    @PostMapping
    public ResponseEntity<FolderMemberDTO> add(
            @PathVariable UUID folderId,
            @Valid @RequestBody GrantRoleRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!folderPermissionService.hasPermission(folderId, userId, FolderPermission.SHARE))
            throw new AccessDeniedException("Нет прав на управление участниками папки");

        FolderRole targetRole = parseFolderRole(request.role());
        FolderRole callerRole = folderPermissionService.getEffectiveRole(folderId, userId);
        if (targetRole == FolderRole.OWNER && callerRole != FolderRole.OWNER)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Нельзя назначить роль OWNER");

        folderPermissionService.grantRole(folderId, request.userId(), targetRole, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                folderPermissionService.listMembers(folderId, userId).stream()
                        .filter(m -> m.userId().equals(request.userId()))
                        .findFirst()
                        .orElseThrow()
        );
    }

    @PutMapping("/{targetUserId}")
    public ResponseEntity<FolderMemberDTO> update(
            @PathVariable UUID folderId,
            @PathVariable UUID targetUserId,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!folderPermissionService.hasPermission(folderId, userId, FolderPermission.SHARE))
            throw new AccessDeniedException("Нет прав на управление участниками папки");

        FolderRole currentRole = folderPermissionService.getEffectiveRole(folderId, targetUserId);
        if (currentRole == FolderRole.OWNER)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Нельзя изменить роль OWNER");

        FolderRole newRole = parseFolderRole(request.role());
        folderPermissionService.grantRole(folderId, targetUserId, newRole, userId);

        return ResponseEntity.ok(
                folderPermissionService.listMembers(folderId, userId).stream()
                        .filter(m -> m.userId().equals(targetUserId))
                        .findFirst()
                        .orElseThrow()
        );
    }

    @DeleteMapping("/{targetUserId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID folderId,
            @PathVariable UUID targetUserId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = ownerResolver.resolve(userDetails.getUsername());
        if (!folderPermissionService.hasPermission(folderId, userId, FolderPermission.SHARE))
            throw new AccessDeniedException("Нет прав на управление участниками папки");

        FolderRole targetRole = folderPermissionService.getEffectiveRole(folderId, targetUserId);
        if (targetRole == FolderRole.OWNER)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Нельзя удалить OWNER из участников");

        folderPermissionService.revokeRole(folderId, targetUserId, userId);
        return ResponseEntity.noContent().build();
    }

    private FolderRole parseFolderRole(String role) {
        try {
            return FolderRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестная роль: " + role);
        }
    }
}
