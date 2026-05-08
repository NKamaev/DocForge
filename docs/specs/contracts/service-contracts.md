# Service contracts

## Module boundaries

Modules communicate only through `*Service` interfaces in their respective `service/` packages.

## Core services

| Service | Module | Responsibility |
|---------|--------|---------------|
| `UserService` | `user` | Authentication, user CRUD |
| `TemplateService` | `template` | Template CRUD, publish, validate |
| `DocumentService` | `document` | Document CRUD, generate, body updates |
| `DocumentPermissionService` | `document` | Access control for documents |
| `FolderService` | `document` | Folder tree CRUD |
| `FolderPermissionService` | `document` | Folder access control |
| `DirectoryService` | `directory` | Directory registries and entries |
| `ExportService` | `export` | DOCX and PDF export |
| `CollaborationBroadcaster` | `collaboration` | Yjs WebSocket broadcast |

## Rules

- Services return DTOs, never entities
- Services call repositories of their own module only
- Cross-module calls go through service interfaces
- `@Transactional` on service methods where needed
