package com.docforge.document;

import com.docforge.document.dto.FolderRequest;
import com.docforge.document.dto.FolderResponse;
import com.docforge.document.model.FolderPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;
    private final FolderPermissionService folderPermissionService;
    private final DocumentOwnerResolver ownerResolver;

    /** GET /api/folders — папки текущего пользователя */
    @GetMapping
    public ResponseEntity<List<FolderResponse>> list(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID ownerId = ownerResolver.resolve(userDetails.getUsername());
        return ResponseEntity.ok(folderService.list(ownerId));
    }

    /** POST /api/folders — создать папку */
    @PostMapping
    public ResponseEntity<FolderResponse> create(
            @Valid @RequestBody FolderRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID ownerId = ownerResolver.resolve(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(folderService.create(ownerId, request));
    }

    /** PUT /api/folders/{id} — переименовать / сменить цвет */
    @PutMapping("/{id}")
    public ResponseEntity<FolderResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody FolderRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID ownerId = ownerResolver.resolve(userDetails.getUsername());
        if (!folderPermissionService.hasPermission(id, ownerId, FolderPermission.MANAGE))
            throw new AccessDeniedException("Нет прав на изменение папки");
        return ResponseEntity.ok(folderService.update(id, ownerId, request));
    }

    /** DELETE /api/folders/{id} — удалить папку (документы переходят в «Без папки») */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID ownerId = ownerResolver.resolve(userDetails.getUsername());
        if (!folderPermissionService.hasPermission(id, ownerId, FolderPermission.DELETE))
            throw new AccessDeniedException("Нет прав на удаление папки");
        folderService.delete(id, ownerId);
        return ResponseEntity.noContent().build();
    }
}
