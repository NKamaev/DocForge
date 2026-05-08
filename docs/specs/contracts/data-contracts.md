# Data contracts

## PostgreSQL (JPA + Flyway)

- Naming: `snake_case`
- Migrations: `V{NNN}__{description}.sql` in `db/migration/`
- UUID PKs via `gen_random_uuid()`
- `TIMESTAMPTZ` for all timestamps
- `JSONB` for flexible metadata (audit details)
- Index on `(entity_type, entity_id)` for audit log

### Key tables

- `users` — authentication and profile
- `template_registry` — template metadata (body in MongoDB)
- `document_registry` — document metadata (body in MongoDB)
- `document_participants` — collaboration roles
- `audit_log` — action history
- `directories` — directory registries
- `directory_schema_fields` — field definitions for directories

## MongoDB

- Collections: `templates`, `documents`, `directories`
- Body storage: TipTap JSON (rich text with custom nodes)
- Embedded arrays for parameters, questions, directory entries
- `_id` matches PostgreSQL registry UUID

## Key conventions

- `template_registry.id` == `templates._id`
- `document_registry.id` == `documents._id`
- MongoDB dates: `Instant` (not `OffsetDateTime`)
- PostgreSQL jsonb: `@JdbcTypeCode(SqlTypes.JSON)`
