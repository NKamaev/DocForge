package com.docforge.document;

import com.docforge.document.model.DocumentMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentMemberRepository extends JpaRepository<DocumentMember, UUID> {

    Optional<DocumentMember> findByDocumentIdAndUserId(UUID documentId, UUID userId);

    List<DocumentMember> findByDocumentId(UUID documentId);

    void deleteByDocumentIdAndUserId(UUID documentId, UUID userId);
}
