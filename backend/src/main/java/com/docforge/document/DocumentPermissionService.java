package com.docforge.document;

import com.docforge.document.dto.DocumentMemberDTO;
import com.docforge.document.model.DocumentPermission;
import com.docforge.document.model.DocumentRole;

import java.util.List;
import java.util.UUID;

public interface DocumentPermissionService {

    /** Разрешение: owner → direct_member → folder_inherit. Null = нет доступа. */
    DocumentRole getEffectiveRole(UUID documentId, UUID userId);

    boolean hasPermission(UUID documentId, UUID userId, DocumentPermission permission);

    void grantRole(UUID documentId, UUID targetUserId, DocumentRole role, UUID grantedBy);

    void revokeRole(UUID documentId, UUID targetUserId, UUID revokedBy);

    List<DocumentMemberDTO> listMembers(UUID documentId, UUID requestingUserId);
}
