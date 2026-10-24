-- Make the legacy publication state of template_version explicit (BUG-058).
--
-- The `bump_type` column was added in V015, but rows created before that
-- carry NULL there. Historically only *publish* wrote to template_version
-- (see the note in V008: "one row per published snapshot"), so every
-- legacy row with NULL bump_type is a publication.
--
-- The backend currently interprets NULL bump_type as published, but relying
-- on a NULL convention is neither explicit nor auditable. This migration
-- labels the legacy rows as 'PUBLISH' so the stored state matches the
-- intended semantics.
--
-- Idempotent: only rows with NULL bump_type are touched, and the UPDATE is
-- a no-op once they have been labelled.

UPDATE template_version
SET bump_type = 'PUBLISH'
WHERE bump_type IS NULL;
