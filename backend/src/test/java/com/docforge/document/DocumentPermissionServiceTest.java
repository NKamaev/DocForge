package com.docforge.document;

import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.document.dto.DocumentMemberDTO;
import com.docforge.document.model.*;
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
class DocumentPermissionServiceTest {

    @Mock private DocumentRegistryRepository registryRepository;
    @Mock private DocumentMemberRepository documentMemberRepository;
    @Mock private FolderPermissionService folderPermissionService;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private DocumentPermissionServiceImpl service;

    // ── getEffectiveRole ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getEffectiveRole() returns OWNER for document owner")
    void getEffectiveRole_ownerReturnsOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(registryRepository.findById(docId)).thenReturn(Optional.of(registry(docId, ownerId, null)));

        DocumentRole role = service.getEffectiveRole(docId, ownerId);

        assertThat(role).isEqualTo(DocumentRole.OWNER);
    }

    @Test
    @DisplayName("getEffectiveRole() returns direct member role")
    void getEffectiveRole_directMemberRole() {
        UUID ownerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        when(registryRepository.findById(docId)).thenReturn(Optional.of(registry(docId, ownerId, null)));
        when(documentMemberRepository.findByDocumentIdAndUserId(docId, userId))
                .thenReturn(Optional.of(member(docId, userId, "EDITOR", "DIRECT")));

        DocumentRole role = service.getEffectiveRole(docId, userId);

        assertThat(role).isEqualTo(DocumentRole.EDITOR);
    }

    @Test
    @DisplayName("getEffectiveRole() falls back to folder role when no direct member")
    void getEffectiveRole_inheritsFolderRole() {
        UUID ownerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();

        when(registryRepository.findById(docId)).thenReturn(Optional.of(registry(docId, ownerId, folderId)));
        when(documentMemberRepository.findByDocumentIdAndUserId(docId, userId)).thenReturn(Optional.empty());
        when(folderPermissionService.getEffectiveRole(folderId, userId)).thenReturn(FolderRole.VIEWER);

        DocumentRole role = service.getEffectiveRole(docId, userId);

        assertThat(role).isEqualTo(DocumentRole.VIEWER);
    }

    @Test
    @DisplayName("getEffectiveRole() returns null when no access at all")
    void getEffectiveRole_noAccess_returnsNull() {
        UUID ownerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        when(registryRepository.findById(docId)).thenReturn(Optional.of(registry(docId, ownerId, null)));
        when(documentMemberRepository.findByDocumentIdAndUserId(docId, userId)).thenReturn(Optional.empty());

        DocumentRole role = service.getEffectiveRole(docId, userId);

        assertThat(role).isNull();
    }

    @Test
    @DisplayName("getEffectiveRole() throws ResourceNotFoundException for missing document")
    void getEffectiveRole_missingDocument_throws() {
        UUID docId = UUID.randomUUID();
        when(registryRepository.findById(docId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEffectiveRole(docId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── hasPermission ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("hasPermission() OWNER implies all permissions")
    void hasPermission_ownerImpliesAll() {
        UUID ownerId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(registryRepository.findById(docId)).thenReturn(Optional.of(registry(docId, ownerId, null)));

        for (DocumentPermission p : DocumentPermission.values()) {
            assertThat(service.hasPermission(docId, ownerId, p)).isTrue();
        }
    }

    @Test
    @DisplayName("hasPermission() VIEWER implies VIEW and READ only")
    void hasPermission_viewerImpliesViewAndRead() {
        UUID ownerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        when(registryRepository.findById(docId)).thenReturn(Optional.of(registry(docId, ownerId, null)));
        when(documentMemberRepository.findByDocumentIdAndUserId(docId, userId))
                .thenReturn(Optional.of(member(docId, userId, "VIEWER", "DIRECT")));

        assertThat(service.hasPermission(docId, userId, DocumentPermission.VIEW)).isTrue();
        assertThat(service.hasPermission(docId, userId, DocumentPermission.READ)).isTrue();
        assertThat(service.hasPermission(docId, userId, DocumentPermission.WRITE)).isFalse();
    }

    @Test
    @DisplayName("hasPermission() returns false for user with no access")
    void hasPermission_noAccess_returnsFalse() {
        UUID ownerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        when(registryRepository.findById(docId)).thenReturn(Optional.of(registry(docId, ownerId, null)));
        when(documentMemberRepository.findByDocumentIdAndUserId(docId, userId)).thenReturn(Optional.empty());

        assertThat(service.hasPermission(docId, userId, DocumentPermission.VIEW)).isFalse();
    }

    // ── grantRole ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("grantRole() creates new member when none exists")
    void grantRole_createsNewMember() {
        UUID ownerId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID grantedBy = UUID.randomUUID();

        when(registryRepository.findById(docId)).thenReturn(Optional.of(registry(docId, ownerId, null)));
        when(documentMemberRepository.findByDocumentIdAndUserId(docId, targetId)).thenReturn(Optional.empty());
        when(documentMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.grantRole(docId, targetId, DocumentRole.EDITOR, grantedBy);

        ArgumentCaptor<DocumentMember> captor = ArgumentCaptor.forClass(DocumentMember.class);
        verify(documentMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("EDITOR");
        assertThat(captor.getValue().getSource()).isEqualTo("DIRECT");
        assertThat(captor.getValue().getGrantedBy()).isEqualTo(grantedBy);
    }

    @Test
    @DisplayName("grantRole() updates existing member role")
    void grantRole_updatesExistingMember() {
        UUID ownerId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        DocumentMember existing = member(docId, targetId, "VIEWER", "DIRECT");

        when(registryRepository.findById(docId)).thenReturn(Optional.of(registry(docId, ownerId, null)));
        when(documentMemberRepository.findByDocumentIdAndUserId(docId, targetId)).thenReturn(Optional.of(existing));
        when(documentMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.grantRole(docId, targetId, DocumentRole.MANAGER, UUID.randomUUID());

        assertThat(existing.getRole()).isEqualTo("MANAGER");
    }

    @Test
    @DisplayName("grantRole() throws ResourceNotFoundException when document not found")
    void grantRole_missingDocument_throws() {
        UUID docId = UUID.randomUUID();
        when(registryRepository.findById(docId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grantRole(docId, UUID.randomUUID(), DocumentRole.VIEWER, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── revokeRole ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("revokeRole() calls deleteByDocumentIdAndUserId")
    void revokeRole_deletesEntry() {
        UUID docId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        service.revokeRole(docId, targetId, UUID.randomUUID());

        verify(documentMemberRepository).deleteByDocumentIdAndUserId(docId, targetId);
    }

    // ── listMembers ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listMembers() maps members with user info")
    void listMembers_mapsWithUserInfo() {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DocumentMember m = member(docId, userId, "EDITOR", "DIRECT");

        User user = new User();
        user.setId(userId);
        user.setLogin("bob");
        user.setDisplayName("Bob");

        when(documentMemberRepository.findByDocumentId(docId)).thenReturn(List.of(m));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        List<DocumentMemberDTO> result = service.listMembers(docId, UUID.randomUUID());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userLogin()).isEqualTo("bob");
        assertThat(result.get(0).role()).isEqualTo("EDITOR");
        assertThat(result.get(0).source()).isEqualTo("DIRECT");
    }

    // ── Role.implies matrix ────────────────────────────────────────────────────

    @Test
    @DisplayName("DocumentRole MANAGER implies everything except SHARE and ADMIN")
    void documentRole_managerPermissions() {
        assertThat(DocumentRole.MANAGER.implies(DocumentPermission.VIEW)).isTrue();
        assertThat(DocumentRole.MANAGER.implies(DocumentPermission.READ)).isTrue();
        assertThat(DocumentRole.MANAGER.implies(DocumentPermission.WRITE)).isTrue();
        assertThat(DocumentRole.MANAGER.implies(DocumentPermission.MANAGE)).isTrue();
        assertThat(DocumentRole.MANAGER.implies(DocumentPermission.DELETE)).isTrue();
        assertThat(DocumentRole.MANAGER.implies(DocumentPermission.SHARE)).isFalse();
        assertThat(DocumentRole.MANAGER.implies(DocumentPermission.ADMIN)).isFalse();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static DocumentRegistry registry(UUID id, UUID ownerId, UUID folderId) {
        DocumentRegistry r = new DocumentRegistry();
        r.setId(id);
        r.setName("Test Doc");
        r.setOwnerId(ownerId);
        r.setFolderId(folderId);
        return r;
    }

    private static DocumentMember member(UUID docId, UUID userId, String role, String source) {
        DocumentMember m = new DocumentMember();
        m.setId(UUID.randomUUID());
        m.setDocumentId(docId);
        m.setUserId(userId);
        m.setRole(role);
        m.setSource(source);
        m.setGrantedAt(Instant.now());
        return m;
    }
}
