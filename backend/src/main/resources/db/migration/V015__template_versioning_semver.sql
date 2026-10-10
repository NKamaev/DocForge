-- Semantic versioning for templates
-- Add major/minor version columns to template_registry
ALTER TABLE template_registry
    ADD COLUMN major_version INT NOT NULL DEFAULT 1,
    ADD COLUMN minor_version INT NOT NULL DEFAULT 0;

-- Add bump metadata to template_version
ALTER TABLE template_version
    ADD COLUMN bump_type         VARCHAR(10),
    ADD COLUMN auto_description  TEXT,
    ADD COLUMN rollback_from     INT;

-- Sync existing version counts: set major_version = version, minor_version = 0
UPDATE template_registry
SET major_version = version,
    minor_version = 0
WHERE major_version = 1 AND minor_version = 0 AND version > 0;
