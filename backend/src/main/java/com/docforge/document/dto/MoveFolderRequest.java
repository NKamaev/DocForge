package com.docforge.document.dto;

import java.util.UUID;

/** Body for PATCH /api/documents/{id}/folder. folderId may be null to unassign. */
public record MoveFolderRequest(UUID folderId) {}
