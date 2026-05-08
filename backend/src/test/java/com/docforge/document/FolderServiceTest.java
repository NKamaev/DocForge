package com.docforge.document;

import com.docforge.common.exception.BusinessException;
import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.document.dto.FolderRequest;
import com.docforge.document.dto.FolderResponse;
import com.docforge.document.model.Folder;
import com.docforge.document.model.FolderPermission;
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
class FolderServiceTest {

    @Mock private FolderRepository folderRepository;
    @Mock private DocumentRegistryRepository documentRegistryRepository;
    @Mock private FolderPermissionService folderPermissionService;

    @InjectMocks
    private FolderServiceImpl folderService;

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create() saves folder with given name, color and ownerId")
    void create_savesFolder() {
        UUID ownerId = UUID.randomUUID();
        FolderRequest request = new FolderRequest("Контракты", "#4CAF50", null);

        Folder saved = folder(UUID.randomUUID(), "Контракты", "#4CAF50", ownerId);
        when(folderRepository.save(any())).thenReturn(saved);
        when(documentRegistryRepository.countByFolderId(saved.getId())).thenReturn(0L);

        FolderResponse response = folderService.create(ownerId, request);

        assertThat(response.name()).isEqualTo("Контракты");
        assertThat(response.color()).isEqualTo("#4CAF50");
        assertThat(response.documentCount()).isEqualTo(0);

        ArgumentCaptor<Folder> captor = ArgumentCaptor.forClass(Folder.class);
        verify(folderRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerId()).isEqualTo(ownerId);
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("list() returns folders for given owner sorted by createdAt")
    void list_returnsFoldersForOwner() {
        UUID ownerId = UUID.randomUUID();
        Folder f1 = folder(UUID.randomUUID(), "Папка А", null, ownerId);
        Folder f2 = folder(UUID.randomUUID(), "Папка Б", "#FF5722", ownerId);

        when(folderRepository.findAccessibleByUser(ownerId)).thenReturn(List.of(f1, f2));
        when(documentRegistryRepository.countByFolderId(any())).thenReturn(3L);

        List<FolderResponse> result = folderService.list(ownerId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Папка А");
        assertThat(result.get(1).documentCount()).isEqualTo(3);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update() changes name and color when user has MANAGE permission")
    void update_changesNameAndColor() {
        UUID ownerId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        Folder existing = folder(folderId, "Старое", "#AAA", ownerId);

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(existing));
        when(folderPermissionService.hasPermission(folderId, ownerId, FolderPermission.MANAGE)).thenReturn(true);
        when(folderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentRegistryRepository.countByFolderId(folderId)).thenReturn(5L);

        FolderResponse response = folderService.update(folderId, ownerId, new FolderRequest("Новое", "#BBB", null));

        assertThat(response.name()).isEqualTo("Новое");
        assertThat(response.color()).isEqualTo("#BBB");
        assertThat(response.documentCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("update() throws ResourceNotFoundException when folder not found")
    void update_folderNotFound_throws() {
        UUID folderId = UUID.randomUUID();
        when(folderRepository.findById(folderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> folderService.update(folderId, UUID.randomUUID(), new FolderRequest("X", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("update() throws BusinessException when user has no MANAGE permission")
    void update_noPermission_throws() {
        UUID folderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Folder existing = folder(folderId, "Папка", null, UUID.randomUUID());

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(existing));
        when(folderPermissionService.hasPermission(folderId, userId, FolderPermission.MANAGE)).thenReturn(false);

        assertThatThrownBy(() -> folderService.update(folderId, userId, new FolderRequest("X", null, null)))
                .isInstanceOf(BusinessException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete() calls deleteById when user has MANAGE permission")
    void delete_callsDeleteById() {
        UUID ownerId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        Folder existing = folder(folderId, "Папка", null, ownerId);

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(existing));
        when(folderPermissionService.hasPermission(folderId, ownerId, FolderPermission.MANAGE)).thenReturn(true);

        folderService.delete(folderId, ownerId);

        verify(folderRepository).deleteById(folderId);
    }

    @Test
    @DisplayName("delete() throws BusinessException when no MANAGE permission")
    void delete_noPermission_throws() {
        UUID folderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Folder existing = folder(folderId, "Папка", null, UUID.randomUUID());

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(existing));
        when(folderPermissionService.hasPermission(folderId, userId, FolderPermission.MANAGE)).thenReturn(false);

        assertThatThrownBy(() -> folderService.delete(folderId, userId))
                .isInstanceOf(BusinessException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Folder folder(UUID id, String name, String color, UUID ownerId) {
        Folder f = new Folder();
        f.setId(id);
        f.setName(name);
        f.setColor(color);
        f.setOwnerId(ownerId);
        f.setCreatedAt(Instant.now());
        return f;
    }
}
