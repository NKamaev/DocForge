---
name: flyway-migration
description: Creating and running Flyway database migrations
---

## Steps

### 1. Create migration file
- Location: `backend/src/main/resources/db/migration/`
- Naming: `V{NNN}__{description}.sql` (e.g. `V005__add_directory_fields.sql`)
- Use sequential numbers, check existing migrations first

### 2. Write SQL
- Idempotent where possible: `IF NOT EXISTS`, `CREATE OR REPLACE`
- Include both `CREATE` and `COMMENT ON` for columns
- Wrap data migrations in a transaction block
```sql
-- V005__add_directory_fields.sql
ALTER TABLE directory_schema_field ADD COLUMN IF NOT EXISTS is_required BOOLEAN DEFAULT FALSE;
COMMENT ON COLUMN directory_schema_field.is_required IS 'Whether field must be filled';
```

### 3. Test locally
```bash
cd backend && ./gradlew bootRun
# Check logs: "Successfully applied 1 migration to schema 'public'"
```

### 4. Rollback strategy
- Flyway does not support automatic rollback
- Create a separate `V{NNN}__undo__{description}.sql` if rollback is needed
- Or write a new migration that reverses the change

### 5. Production
- Never edit an existing migration that has been run
- Always create a new migration file
- Use `flyway.repair` if checksums get out of sync
