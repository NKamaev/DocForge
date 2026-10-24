-- Backfill template_registry.owner_id for legacy rows (BUG-068).
--
-- The template module now requires the caller to be the owner of a template
-- (or an admin) in order to modify it. `template_registry.owner_id` is
-- nullable, and rows created before the column was populated keep NULL there.
-- Such templates would silently become admin-only — their real authors could
-- no longer edit, publish or roll back them.
--
-- The owner is reconstructed from the best available source of truth, in this
-- order:
--   1. the author of the *earliest* version of the template
--      (template_version.created_by of the oldest template_version row of the
--      template, ordered by created_at);
--   2. the user of the newest CREATE audit event of the template
--      (audit_log where entity_type = 'TEMPLATE' and action = 'CREATE').
--
-- Rows that match neither source keep owner_id = NULL and therefore stay
-- admin-only (fail closed). Owners are never invented, no rows are deleted
-- and the publication state is not touched.
--
-- Idempotent: both statements only touch rows with owner_id IS NULL, so a
-- re-run is a no-op.

-- 1. Author of the earliest version of the template.
WITH earliest_version AS (
    SELECT DISTINCT ON (tv.template_id)
           tv.template_id,
           tv.created_by
    FROM template_version tv
    ORDER BY tv.template_id, tv.created_at ASC, tv.id ASC
)
UPDATE template_registry t
SET owner_id = ev.created_by
FROM earliest_version ev
WHERE ev.template_id = t.id
  AND ev.created_by IS NOT NULL
  AND t.owner_id IS NULL;

-- 2. Author of the newest CREATE audit event (fallback for templates without
--    a recorded version author).
WITH create_audit AS (
    SELECT DISTINCT ON (a.entity_id)
           a.entity_id,
           a.user_id
    FROM audit_log a
    WHERE a.entity_type = 'TEMPLATE'
      AND a.action = 'CREATE'
      AND a.user_id IS NOT NULL
    ORDER BY a.entity_id, a.created_at DESC, a.id DESC
)
UPDATE template_registry t
SET owner_id = ca.user_id
FROM create_audit ca
WHERE ca.entity_id = t.id
  AND t.owner_id IS NULL;
