package com.docforge.document;

import com.docforge.document.dto.FolderRequest;
import com.docforge.document.dto.FolderResponse;

import java.util.List;
import java.util.UUID;

public interface FolderService {

    FolderResponse create(UUID ownerId, FolderRequest request);

    List<FolderResponse> list(UUID ownerId);

    FolderResponse update(UUID id, UUID ownerId, FolderRequest request);

    void delete(UUID id, UUID ownerId);
}
