package com.docforge.document;

import com.docforge.document.model.DocumentMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Репозиторий участников документа (таблица document_members после V012).
 */
public interface DocumentParticipantRepository extends JpaRepository<DocumentMember, UUID> {

    List<DocumentMember> findByDocumentId(UUID documentId);

    void deleteByDocumentIdAndUserId(UUID documentId, UUID userId);
}
