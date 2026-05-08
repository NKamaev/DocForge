package com.docforge.document;

import com.docforge.common.exception.BusinessException;
import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.document.dto.FolderRequest;
import com.docforge.document.dto.FolderResponse;
import com.docforge.document.model.Folder;
import com.docforge.document.model.FolderPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {

    private final FolderRepository folderRepository;
    private final DocumentRegistryRepository documentRegistryRepository;
    private final FolderPermissionService folderPermissionService;

    @Override
    @Transactional
    public FolderResponse create(UUID ownerId, FolderRequest request) {
        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setName(request.name());
        folder.setColor(request.color());
        folder.setOwnerId(ownerId);
        folder.setParentId(request.parentId());
        folder = folderRepository.save(folder);
        log.info("Создана папка id={} name='{}' ownerId={}", folder.getId(), folder.getName(), ownerId);
        return toResponse(folder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderResponse> list(UUID ownerId) {
        return folderRepository.findAccessibleByUser(ownerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public FolderResponse update(UUID id, UUID ownerId, FolderRequest request) {
        Folder folder = findAndVerifyOwner(id, ownerId);
        folder.setName(request.name());
        folder.setColor(request.color());
        folder = folderRepository.save(folder);
        log.info("Обновлена папка id={}", id);
        return toResponse(folder);
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID ownerId) {
        findAndVerifyOwner(id, ownerId);
        // ON DELETE SET NULL в миграции обнулит folder_id у документов в этой папке
        folderRepository.deleteById(id);
        log.info("Удалена папка id={}", id);
    }

    private Folder findAndVerifyOwner(UUID id, UUID ownerId) {
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Папка не найдена: " + id));
        if (ownerId != null && !folderPermissionService.hasPermission(id, ownerId, FolderPermission.MANAGE)) {
            throw new BusinessException("Нет доступа к папке: " + id);
        }
        return folder;
    }

    private FolderResponse toResponse(Folder folder) {
        long count = documentRegistryRepository.countByFolderId(folder.getId());
        return new FolderResponse(folder.getId(), folder.getName(), folder.getColor(), count, folder.getParentId());
    }
}
