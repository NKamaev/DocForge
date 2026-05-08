package com.docforge.document;

import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.document.dto.FolderMemberDTO;
import com.docforge.document.model.Folder;
import com.docforge.document.model.FolderMember;
import com.docforge.document.model.FolderPermission;
import com.docforge.document.model.FolderRole;
import com.docforge.user.User;
import com.docforge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FolderPermissionServiceImpl implements FolderPermissionService {

    private final FolderRepository folderRepository;
    private final FolderMemberRepository folderMemberRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public FolderRole getEffectiveRole(UUID folderId, UUID userId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Папка не найдена: " + folderId));
        if (userId.equals(folder.getOwnerId())) return FolderRole.OWNER;
        return folderMemberRepository.findByFolderIdAndUserId(folderId, userId)
                .map(m -> FolderRole.valueOf(m.getRole()))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(UUID folderId, UUID userId, FolderPermission permission) {
        FolderRole role = getEffectiveRole(folderId, userId);
        return role != null && role.implies(permission);
    }

    @Override
    @Transactional
    public void grantRole(UUID folderId, UUID targetUserId, FolderRole role, UUID grantedBy) {
        folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Папка не найдена: " + folderId));
        FolderMember member = folderMemberRepository
                .findByFolderIdAndUserId(folderId, targetUserId)
                .orElseGet(() -> {
                    FolderMember m = new FolderMember();
                    m.setFolderId(folderId);
                    m.setUserId(targetUserId);
                    return m;
                });
        member.setRole(role.name());
        member.setGrantedBy(grantedBy);
        folderMemberRepository.save(member);
        log.info("Выдана роль {} на папку {} пользователю {}", role, folderId, targetUserId);
    }

    @Override
    @Transactional
    public void revokeRole(UUID folderId, UUID targetUserId, UUID revokedBy) {
        folderMemberRepository.deleteByFolderIdAndUserId(folderId, targetUserId);
        log.info("Отозвана роль на папку {} у пользователя {}", folderId, targetUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderMemberDTO> listMembers(UUID folderId, UUID requestingUserId) {
        return folderMemberRepository.findByFolderId(folderId).stream()
                .map(m -> {
                    User user = userRepository.findById(m.getUserId()).orElse(null);
                    return new FolderMemberDTO(
                            m.getUserId(),
                            user != null ? user.getLogin() : null,
                            user != null ? user.getDisplayName() : null,
                            m.getRole(),
                            m.getGrantedAt()
                    );
                })
                .toList();
    }
}
