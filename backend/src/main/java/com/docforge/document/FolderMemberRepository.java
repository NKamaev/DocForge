package com.docforge.document;

import com.docforge.document.model.FolderMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderMemberRepository extends JpaRepository<FolderMember, UUID> {

    Optional<FolderMember> findByFolderIdAndUserId(UUID folderId, UUID userId);

    List<FolderMember> findByFolderId(UUID folderId);

    void deleteByFolderIdAndUserId(UUID folderId, UUID userId);
}
