package com.docforge.document;

import com.docforge.document.model.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID> {

    List<Folder> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    @Query("""
            SELECT f FROM Folder f
            WHERE f.ownerId = :userId
               OR EXISTS (
                   SELECT 1 FROM FolderMember fm
                   WHERE fm.folderId = f.id AND fm.userId = :userId
               )
            ORDER BY f.createdAt ASC
            """)
    List<Folder> findAccessibleByUser(@Param("userId") UUID userId);
}
