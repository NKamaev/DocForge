package com.docforge.document;

import com.docforge.document.dto.FolderMemberDTO;
import com.docforge.document.model.FolderPermission;
import com.docforge.document.model.FolderRole;

import java.util.List;
import java.util.UUID;

public interface FolderPermissionService {

    /** Эффективная роль с учётом owner_id папки. Null = нет доступа. */
    FolderRole getEffectiveRole(UUID folderId, UUID userId);

    boolean hasPermission(UUID folderId, UUID userId, FolderPermission permission);

    void grantRole(UUID folderId, UUID targetUserId, FolderRole role, UUID grantedBy);

    void revokeRole(UUID folderId, UUID targetUserId, UUID revokedBy);

    List<FolderMemberDTO> listMembers(UUID folderId, UUID requestingUserId);
}
