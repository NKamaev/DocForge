package com.docforge.document;

import com.docforge.AbstractIT;
import com.docforge.document.dto.FolderMemberDTO;
import com.docforge.document.dto.FolderRequest;
import com.docforge.document.dto.FolderResponse;
import com.docforge.document.model.FolderPermission;
import com.docforge.document.model.FolderRole;
import com.docforge.security.JwtTokenProvider;
import com.docforge.user.User;
import com.docforge.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class FolderMembersIT extends AbstractIT {

    @Autowired private FolderService folderService;
    @Autowired private FolderPermissionService folderPermissionService;
    @Autowired private FolderRepository folderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private MockMvc mockMvc;

    private UUID ownerId;
    private UUID viewerId;

    @BeforeEach
    void setUp() {
        // Use unique suffixes to avoid login collisions across test runs
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User owner = new User();
        owner.setLogin("folder-owner-" + suffix);
        owner.setPasswordHash("hash");
        owner.setDisplayName("Owner");
        ownerId = userRepository.save(owner).getId();

        User viewer = new User();
        viewer.setLogin("folder-viewer-" + suffix);
        viewer.setPasswordHash("hash");
        viewer.setDisplayName("Viewer");
        viewerId = userRepository.save(viewer).getId();
    }

    @Test
    @WithMockUser(username = "test")
    @DisplayName("list() возвращает только папки, где пользователь — owner или member")
    void list_returnsAccessibleFolders() {
        // owner creates a folder
        FolderResponse ownerFolder = folderService.create(ownerId, new FolderRequest("Owner Folder", "#ff0000", null));
        // viewer creates a separate folder
        FolderResponse viewerFolder = folderService.create(viewerId, new FolderRequest("Viewer Own Folder", null, null));

        // grant EDITOR to viewer on owner's folder
        folderPermissionService.grantRole(ownerFolder.id(), viewerId, FolderRole.EDITOR, ownerId);

        List<FolderResponse> ownerFolders = folderService.list(ownerId);
        List<FolderResponse> viewerFolders = folderService.list(viewerId);

        // owner sees only their folder (not viewer's)
        assertThat(ownerFolders.stream().map(FolderResponse::id)).contains(ownerFolder.id());
        // viewer sees both their folder and the shared one
        assertThat(viewerFolders.stream().map(FolderResponse::id))
                .contains(viewerFolder.id(), ownerFolder.id());
    }

    @Test
    @WithMockUser(username = "test")
    @DisplayName("hasPermission() — owner всегда имеет все права")
    void hasPermission_ownerHasAll() {
        FolderResponse folder = folderService.create(ownerId, new FolderRequest("Perm Folder", null, null));

        assertThat(folderPermissionService.hasPermission(folder.id(), ownerId, FolderPermission.VIEW)).isTrue();
        assertThat(folderPermissionService.hasPermission(folder.id(), ownerId, FolderPermission.MANAGE)).isTrue();
        assertThat(folderPermissionService.hasPermission(folder.id(), ownerId, FolderPermission.SHARE)).isTrue();
        assertThat(folderPermissionService.hasPermission(folder.id(), ownerId, FolderPermission.DELETE)).isTrue();
    }

    @Test
    @WithMockUser(username = "test")
    @DisplayName("hasPermission() — не-member не имеет доступа")
    void hasPermission_nonMemberDenied() {
        FolderResponse folder = folderService.create(ownerId, new FolderRequest("Private Folder", null, null));

        assertThat(folderPermissionService.hasPermission(folder.id(), viewerId, FolderPermission.VIEW)).isFalse();
        assertThat(folderPermissionService.hasPermission(folder.id(), viewerId, FolderPermission.MANAGE)).isFalse();
    }

    @Test
    @WithMockUser(username = "test")
    @DisplayName("grantRole() + listMembers() — выдача роли и просмотр участников")
    void grantAndListMembers() {
        FolderResponse folder = folderService.create(ownerId, new FolderRequest("Shared Folder", null, null));

        folderPermissionService.grantRole(folder.id(), viewerId, FolderRole.EDITOR, ownerId);

        List<FolderMemberDTO> members = folderPermissionService.listMembers(folder.id(), ownerId);
        assertThat(members).hasSize(2);
        assertThat(members.get(0).userId()).isEqualTo(ownerId);
        assertThat(members.get(0).role()).isEqualTo("OWNER");
        assertThat(members.get(1).userId()).isEqualTo(viewerId);
        assertThat(members.get(1).role()).isEqualTo("EDITOR");

        // viewer now has WRITE via EDITOR role
        assertThat(folderPermissionService.hasPermission(folder.id(), viewerId, FolderPermission.WRITE)).isTrue();
        // but not SHARE
        assertThat(folderPermissionService.hasPermission(folder.id(), viewerId, FolderPermission.SHARE)).isFalse();
    }

    @Test
    @WithMockUser(username = "test")
    @DisplayName("revokeRole() — после отзыва пользователь теряет доступ")
    void revokeRole_removesAccess() {
        FolderResponse folder = folderService.create(ownerId, new FolderRequest("Revoke Folder", null, null));
        folderPermissionService.grantRole(folder.id(), viewerId, FolderRole.EDITOR, ownerId);
        assertThat(folderPermissionService.hasPermission(folder.id(), viewerId, FolderPermission.VIEW)).isTrue();

        folderPermissionService.revokeRole(folder.id(), viewerId, ownerId);

        assertThat(folderPermissionService.hasPermission(folder.id(), viewerId, FolderPermission.VIEW)).isFalse();
        List<FolderMemberDTO> members = folderPermissionService.listMembers(folder.id(), ownerId);
        assertThat(members).hasSize(1);
        assertThat(members.get(0).userId()).isEqualTo(ownerId);
        assertThat(members.get(0).role()).isEqualTo("OWNER");
    }

    @Test
    @WithMockUser(username = "test")
    @DisplayName("getEffectiveRole() — OWNER у создателя, EDITOR у выданного")
    void getEffectiveRole_correct() {
        FolderResponse folder = folderService.create(ownerId, new FolderRequest("Role Check", null, null));
        folderPermissionService.grantRole(folder.id(), viewerId, FolderRole.VIEWER, ownerId);

        assertThat(folderPermissionService.getEffectiveRole(folder.id(), ownerId)).isEqualTo(FolderRole.OWNER);
        assertThat(folderPermissionService.getEffectiveRole(folder.id(), viewerId)).isEqualTo(FolderRole.VIEWER);

        UUID stranger = UUID.randomUUID();
        assertThat(folderPermissionService.getEffectiveRole(folder.id(), stranger)).isNull();
    }

    @Test
    @DisplayName("POST /api/folders/{id}/members — MANAGER grants OWNER → 422, not 500 (shared handler fix)")
    void manager_grantOwner_returns422Not500() throws Exception {
        // given — a folder owned by ownerId with a granted MANAGER
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String managerLogin = "folder-manager-" + suffix;

        User manager = new User();
        manager.setLogin(managerLogin);
        manager.setPasswordHash("hash");
        manager.setDisplayName("Manager");
        UUID managerId = userRepository.save(manager).getId();

        FolderResponse folder = folderService.create(
                ownerId, new FolderRequest("Guarded Folder " + suffix, null, null));
        folderPermissionService.grantRole(folder.id(), managerId, FolderRole.MANAGER, ownerId);

        UUID targetId = UUID.randomUUID();

        // when — the controller throws ResponseStatusException(422)
        mockMvc.perform(post("/api/folders/{id}/members", folder.id())
                        .header("Authorization", "Bearer " + jwtTokenProvider.generateToken(managerLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + targetId + "\",\"role\":\"OWNER\"}"))
                // then — the shared GlobalExceptionHandler keeps the 422 status
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Нельзя назначить роль OWNER"));

        assertThat(folderPermissionService.getEffectiveRole(folder.id(), targetId)).isNull();
    }
}
