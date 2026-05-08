package com.docforge.document.model;

public enum DocumentRole {
    VIEWER, EDITOR, MANAGER, OWNER;

    public boolean implies(DocumentPermission p) {
        return switch (this) {
            case VIEWER  -> p == DocumentPermission.VIEW || p == DocumentPermission.READ;
            case EDITOR  -> p == DocumentPermission.VIEW || p == DocumentPermission.READ || p == DocumentPermission.WRITE;
            case MANAGER -> p != DocumentPermission.ADMIN;
            case OWNER   -> true;
        };
    }
}
