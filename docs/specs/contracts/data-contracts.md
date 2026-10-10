# Data contracts

## PostgreSQL (JPA + Flyway)

- Naming: `snake_case`
- Migrations: `V{NNN}__{description}.sql` in `db/migration/`
- UUID PKs via `gen_random_uuid()`
- `TIMESTAMPTZ` for all timestamps
- `JSONB` for flexible metadata

### Tables

| Table | Flyway | Purpose |
|-------|--------|---------|
| `users` | V001 | Authentication and profile |
| `template_registry` | V002 | Template metadata (body in MongoDB) |
| `document_registry` | V003 | Document metadata (body in MongoDB) |
| `document_participants` | V003 | Collaboration roles |
| `audit_log` | V004 | Action history |
| `directory_registry` | V005 | Directory registries |
| `directory_schema_field` | V005 | Directory field definitions |
| `document_revision` | V010 | Revision history with author and body |
| `folder` | V011 | Folder tree for documents |
| `folder_member` | V012 | RBAC folder members |
| `document_member` | V012 | RBAC document members |
| `template_version` | V015 | Template version metadata (semver) |

### Key conventions

- `template_registry.id` == `templates._id`
- `document_registry.id` == `documents._id`
- PostgreSQL `UUID` ↔ MongoDB `String` — same UUID value

## MongoDB

### Collections

| Collection | Purpose |
|------------|---------|
| `templates` | Full template body (TipTap JSON + parameters + questionnaire + scripts) |
| `documents` | Document body, answers, yjs_state |
| `directories` | Directory entries (embedded array) |

### Conventions

- Body storage: TipTap JSON (rich text with custom nodes)
- Embedded arrays for parameters, questions, directory entries
- `_id` matches PostgreSQL registry UUID
- MongoDB dates: `Instant` (not `OffsetDateTime`)
- PostgreSQL jsonb: `@JdbcTypeCode(SqlTypes.JSON)`
