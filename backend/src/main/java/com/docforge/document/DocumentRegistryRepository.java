package com.docforge.document;

import com.docforge.document.model.DocumentRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DocumentRegistryRepository extends JpaRepository<DocumentRegistry, UUID> {

    Page<DocumentRegistry> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    @Query("""
            SELECT dr FROM DocumentRegistry dr
            WHERE dr.ownerId = :userId
               OR EXISTS (
                   SELECT 1 FROM DocumentMember dm
                   WHERE dm.documentId = dr.id AND dm.userId = :userId
               )
               OR (dr.folderId IS NOT NULL AND EXISTS (
                   SELECT 1 FROM FolderMember fm
                   WHERE fm.folderId = dr.folderId AND fm.userId = :userId
               ))
            ORDER BY dr.createdAt DESC
            """)
    Page<DocumentRegistry> findAccessibleByUser(@Param("userId") UUID userId, Pageable pageable);

    boolean existsByTemplateId(UUID templateId);

    long countByFolderId(UUID folderId);
}
