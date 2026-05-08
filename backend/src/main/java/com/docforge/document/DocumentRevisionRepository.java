package com.docforge.document;

import com.docforge.document.model.DocumentRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRevisionRepository extends JpaRepository<DocumentRevision, UUID> {

    List<DocumentRevision> findByDocumentIdOrderByRevisionNumberDesc(UUID documentId);

    Optional<DocumentRevision> findByDocumentIdAndRevisionNumber(UUID documentId, int revisionNumber);

    @Query("SELECT COALESCE(MAX(r.revisionNumber), 0) FROM DocumentRevision r WHERE r.documentId = :documentId")
    int findMaxRevisionNumber(UUID documentId);
}
