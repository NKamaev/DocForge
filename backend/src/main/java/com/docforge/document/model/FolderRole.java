package com.docforge.document.model;

public enum FolderRole {
    VIEWER, EDITOR, MANAGER, OWNER;

    public boolean implies(FolderPermission p) {
        return switch (this) {
            case VIEWER  -> p == FolderPermission.VIEW || p == FolderPermission.READ;
            case EDITOR  -> p == FolderPermission.VIEW || p == FolderPermission.READ || p == FolderPermission.WRITE;
            case MANAGER -> p != FolderPermission.ADMIN;
            case OWNER   -> true;
        };
    }
}
