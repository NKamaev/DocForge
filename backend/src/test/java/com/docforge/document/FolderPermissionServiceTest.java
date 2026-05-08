package com.docforge.document;

import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.document.dto.FolderMemberDTO;
import com.docforge.document.model.Folder;
import com.docforge.document.model.FolderMember;
import com.docforge.document.model.FolderPermission;
import com.docforge.document.model.FolderRole;
import com.docforge.user.User;
import com.docforge.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FolderPermissionServiceTest {

    @Mock private FolderRepository folderRepository;
    @Mock private FolderMemberRepository folderMemberRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private FolderPermissionServiceImpl service;

    // ── getEffectiveRole ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getEffectiveRole() returns OWNER for folder owner")
    void getEffectiveRole_ownerReturnsOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder(folderId, ownerId)));

        FolderRole role = service.getEffectiveRole(folderId, ownerId);

        assertThat(role).isEqualTo(FolderRole.OWNER);
    }

    @Test
    @DisplayName("getEffectiveRole() returns member role from folder_members")
    void getEffectiveRole_memberRoleFromDb() {
        UUID ownerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder(folderId, ownerId)));
        when(folderMemberRepository.findByFolderIdAndUserId(folderId, userId))
                .thenReturn(Optional.of(member(folderId, userId, "EDITOR")));

        FolderRole role = service.getEffectiveRole(folderId, userId);

        assertThat(role).isEqualTo(FolderRole.EDITOR);
    }

    @Test
    @DisplayName("getEffectiveRole() returns null for unknown user")
    void getEffectiveRole_unknownUserReturnsNull() {
        UUID folderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder(folderId, UUID.randomUUID())));
        when(folderMemberRepository.findByFolderIdAndUserId(folderId, userId)).thenReturn(Optional.empty());

        FolderRole role = service.getEffectiveRole(folderId, userId);

        assertThat(role).isNull();
    }

    @Test
    @DisplayName("getEffectiveRole() throws ResourceNotFoundException for missing folder")
    void getEffectiveRole_missingFolder_throws() {
        UUID folderId = UUID.randomUUID();
        when(folderRepository.findById(folderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEffectiveRole(folderId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── hasPermission ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("hasPermission() VIEWER implies VIEW and READ only")
    void hasPermission_viewerImpliesViewAndRead() {
        UUID folderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder(folderId, UUID.randomUUID())));
        when(folderMemberRepository.findByFolderIdAndUserId(folderId, userId))
                .thenReturn(Optional.of(member(folderId, userId, "VIEWER")));

        assertThat(service.hasPermission(folderId, userId, FolderPermission.VIEW)).isTrue();
        assertThat(service.hasPermission(folderId, userId, FolderPermission.READ)).isTrue();
        assertThat(service.hasPermission(folderId, userId, FolderPermission.WRITE)).isFalse();
        assertThat(service.hasPermission(folderId, userId, FolderPermission.MANAGE)).isFalse();
    }

    @Test
    @DisplayName("hasPermission() OWNER implies all permissions")
    void hasPermission_ownerImpliesAll() {
        UUID ownerId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder(folderId, ownerId)));

        for (FolderPermission p : FolderPermission.values()) {
            assertThat(service.hasPermission(folderId, ownerId, p)).isTrue();
        }
    }

    @Test
    @DisplayName("hasPermission() returns false for user with no access")
    void hasPermission_noAccess_returnsFalse() {
        UUID folderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder(folderId, UUID.randomUUID())));
        when(folderMemberRepository.findByFolderIdAndUserId(folderId, userId)).thenReturn(Optional.empty());

        assertThat(service.hasPermission(folderId, userId, FolderPermission.VIEW)).isFalse();
    }

    // ── grantRole ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("grantRole() creates new member when none exists")
    void grantRole_createsNewMember() {
        UUID folderId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID grantedBy = UUID.randomUUID();

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder(folderId, UUID.randomUUID())));
        when(folderMemberRepository.findByFolderIdAndUserId(folderId, targetId)).thenReturn(Optional.empty());
        when(folderMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.grantRole(folderId, targetId, FolderRole.EDITOR, grantedBy);

        ArgumentCaptor<FolderMember> captor = ArgumentCaptor.forClass(FolderMember.class);
        verify(folderMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("EDITOR");
        assertThat(captor.getValue().getGrantedBy()).isEqualTo(grantedBy);
    }

    @Test
    @DisplayName("grantRole() updates existing member role")
    void grantRole_updatesExistingMember() {
        UUID folderId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        FolderMember existing = member(folderId, targetId, "VIEWER");

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder(folderId, UUID.randomUUID())));
        when(folderMemberRepository.findByFolderIdAndUserId(folderId, targetId)).thenReturn(Optional.of(existing));
        when(folderMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.grantRole(folderId, targetId, FolderRole.MANAGER, UUID.randomUUID());

        assertThat(existing.getRole()).isEqualTo("MANAGER");
    }

    @Test
    @DisplayName("grantRole() throws ResourceNotFoundException when folder not found")
    void grantRole_missingFolder_throws() {
        UUID folderId = UUID.randomUUID();
        when(folderRepository.findById(folderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grantRole(folderId, UUID.randomUUID(), FolderRole.VIEWER, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── revokeRole ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("revokeRole() calls deleteByFolderIdAndUserId")
    void revokeRole_deletesEntry() {
        UUID folderId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        service.revokeRole(folderId, targetId, UUID.randomUUID());

        verify(folderMemberRepository).deleteByFolderIdAndUserId(folderId, targetId);
    }

    // ── listMembers ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listMembers() maps members with user info")
    void listMembers_mapsWithUserInfo() {
        UUID folderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FolderMember m = member(folderId, userId, "VIEWER");

        User user = new User();
        user.setId(userId);
        user.setLogin("alice");
        user.setDisplayName("Alice");

        when(folderMemberRepository.findByFolderId(folderId)).thenReturn(List.of(m));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        List<FolderMemberDTO> result = service.listMembers(folderId, UUID.randomUUID());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userLogin()).isEqualTo("alice");
        assertThat(result.get(0).role()).isEqualTo("VIEWER");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static Folder folder(UUID id, UUID ownerId) {
        Folder f = new Folder();
        f.setId(id);
        f.setName("Test Folder");
        f.setOwnerId(ownerId);
        f.setCreatedAt(Instant.now());
        return f;
    }

    private static FolderMember member(UUID folderId, UUID userId, String role) {
        FolderMember m = new FolderMember();
        m.setId(UUID.randomUUID());
        m.setFolderId(folderId);
        m.setUserId(userId);
        m.setRole(role);
        m.setGrantedAt(Instant.now());
        return m;
    }
}
