-- Repair semver components after BUG-054.
--
-- publish() used to allocate `max(version_number) + 1` while saveVersion() and
-- rollback() used `major * 10000 + minor`. As a result some rows have a
-- semver-shaped version_number (>= 10000) but stale major/minor components
-- (e.g. 10002 / 1.1), which made the next saveVersion() collide on
-- UNIQUE (template_id, version_number).
--
-- The migration is label-only: version_number is never changed, so documents
-- pinned to a version keep resolving.

-- Semver-shaped rows: re-derive components from the number.
UPDATE template_registry
SET major_version = version / 10000,
    minor_version = version % 10000
WHERE version >= 10000
  AND major_version * 10000 + minor_version <> version;

UPDATE template_version
SET major_version = version_number / 10000,
    minor_version = version_number % 10000
WHERE version_number >= 10000
  AND (major_version IS NULL
       OR minor_version IS NULL
       OR major_version * 10000 + minor_version <> version_number);

-- Legacy opaque counters (< 10000) are labelled vN.0, as V015 did for the registry.
UPDATE template_registry
SET major_version = version,
    minor_version = 0
WHERE version < 10000
  AND (major_version IS NULL
       OR minor_version IS NULL
       OR major_version * 10000 + minor_version <> version);

UPDATE template_version
SET major_version = version_number,
    minor_version = 0
WHERE version_number < 10000
  AND (major_version IS NULL
       OR minor_version IS NULL
       OR major_version * 10000 + minor_version <> version_number);
