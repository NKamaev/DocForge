# Service contracts

## Module boundaries

Modules communicate only through `*Service` interfaces in their respective `service/` packages.

## Core services

| Service | Module | Responsibility |
|---------|--------|---------------|
| `UserService` | `user` | Authentication, user CRUD |
| `TemplateService` | `template` | Template CRUD, publish, validate, versioning |
| `DocumentService` | `document` | Document CRUD, generate, body updates, revisions |
| `DocumentPermissionService` | `document` | Access control for documents |
| `FolderService` | `document` | Folder tree CRUD |
| `FolderPermissionService` | `document` | Folder access control |
| `DirectoryService` | `directory` | Directory registries and entries |
| `ExportService` (DocxExport, PdfExport) | `export` | DOCX and PDF export |
| `CollaborationBroadcaster` | `collaboration` | Yjs WebSocket broadcast |
| `YjsDocumentStore` | `collaboration` | Yjs state persistence |
| `AuditService` | `audit` | Action logging and history |
| `ImageStorageService` | `upload` | Image upload and retrieval |

## Rules

- Services return DTOs, never entities
- Services call repositories of their own module only
- Cross-module calls go through service interfaces
- `@Transactional` on service methods where needed
- Never import another module's `*Repository` directly
