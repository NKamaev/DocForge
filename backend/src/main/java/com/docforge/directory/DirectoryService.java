package com.docforge.directory;

import com.docforge.directory.dto.*;

import java.util.List;
import java.util.UUID;

public interface DirectoryService {
    List<DirectoryListItemResponse> list();
    DirectoryResponse getById(UUID id);
    DirectoryResponse create(UUID ownerId, CreateDirectoryRequest req);
    DirectoryResponse update(UUID id, UpdateDirectoryRequest req);
    void delete(UUID id);

    DirectoryResponse addEntry(UUID directoryId, DirectoryEntryRequest req);
    DirectoryResponse updateEntry(UUID directoryId, String entryId, DirectoryEntryRequest req);
    DirectoryResponse deleteEntry(UUID directoryId, String entryId);
}
