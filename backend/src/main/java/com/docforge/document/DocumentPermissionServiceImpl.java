package com.docforge.document;

import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.common.exception.ValidationException;
import com.docforge.document.dto.DocumentMemberDTO;
import com.docforge.document.model.DocumentMember;
import com.docforge.document.model.DocumentPermission;
import com.docforge.document.model.DocumentRegistry;
import com.docforge.document.model.DocumentRole;
import com.docforge.document.model.FolderRole;
import com.docforge.user.User;
import com.docforge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentPermissionServiceImpl implements DocumentPermissionService {

    private final DocumentRegistryRepository registryRepository;
    private final DocumentMemberRepository documentMemberRepository;
    private final FolderPermissionService folderPermissionService;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public DocumentRole getEffectiveRole(UUID documentId, UUID userId) {
        DocumentRegistry reg = registryRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Документ не найден: " + documentId));
        if (userId.equals(reg.getOwnerId())) return DocumentRole.OWNER;
        DocumentMember dm = documentMemberRepository.findByDocumentIdAndUserId(documentId, userId).orElse(null);
        if (dm != null) return DocumentRole.valueOf(dm.getRole());
        if (reg.getFolderId() != null) {
            FolderRole fr = folderPermissionService.getEffectiveRole(reg.getFolderId(), userId);
            if (fr != null) return DocumentRole.valueOf(fr.name());
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(UUID documentId, UUID userId, DocumentPermission permission) {
        DocumentRole role = getEffectiveRole(documentId, userId);
        return role != null && role.implies(permission);
    }

    @Override
    @Transactional
    public void grantRole(UUID documentId, UUID targetUserId, DocumentRole role, UUID grantedBy) {
        registryRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Документ не найден: " + documentId));

        // Роль владельца неизменяема: нельзя ни понизить выданного OWNER, ни
        // «назначить» роль самому владельцу документа (upsert создал бы
        // невидимую строку участника).
        if (getEffectiveRole(documentId, targetUserId) == DocumentRole.OWNER) {
            throw new ValidationException("Нельзя изменить роль владельца документа", Map.of());
        }

        DocumentMember member = documentMemberRepository
                .findByDocumentIdAndUserId(documentId, targetUserId)
                .orElseGet(() -> {
                    DocumentMember m = new DocumentMember();
                    m.setDocumentId(documentId);
                    m.setUserId(targetUserId);
                    m.setSource("DIRECT");
                    return m;
                });
        member.setRole(role.name());
        member.setGrantedBy(grantedBy);
        documentMemberRepository.save(member);
        log.info("Выдана роль {} на документ {} пользователю {}", role, documentId, targetUserId);
    }

    @Override
    @Transactional
    public void revokeRole(UUID documentId, UUID targetUserId, UUID revokedBy) {
        documentMemberRepository.deleteByDocumentIdAndUserId(documentId, targetUserId);
        log.info("Отозвана роль на документ {} у пользователя {}", documentId, targetUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentMemberDTO> listMembers(UUID documentId, UUID requestingUserId) {
        DocumentRegistry reg = registryRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Документ не найден: " + documentId));

        var directMembers = documentMemberRepository.findByDocumentId(documentId).stream()
                .filter(m -> !m.getUserId().equals(reg.getOwnerId()))
                .map(m -> {
                    User user = userRepository.findById(m.getUserId()).orElse(null);
                    return new DocumentMemberDTO(
                            m.getUserId(),
                            user != null ? user.getLogin() : null,
                            user != null ? user.getDisplayName() : null,
                            m.getRole(),
                            m.getSource(),
                            m.getGrantedAt()
                    );
                })
                .toList();

        User owner = userRepository.findById(reg.getOwnerId()).orElse(null);
        var ownerMember = new DocumentMemberDTO(
                reg.getOwnerId(),
                owner != null ? owner.getLogin() : null,
                owner != null ? owner.getDisplayName() : null,
                DocumentRole.OWNER.name(),
                "DIRECT",
                reg.getCreatedAt()
        );

        var result = new java.util.ArrayList<DocumentMemberDTO>();
        result.add(ownerMember);
        result.addAll(directMembers);
        return result;
    }
}
