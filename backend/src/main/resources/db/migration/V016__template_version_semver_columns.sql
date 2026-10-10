-- Add major/minor version columns to template_version for direct storage
-- (V015 added these to template_registry; this adds them to template_version)
ALTER TABLE template_version
    ADD COLUMN major_version     INT,
    ADD COLUMN minor_version     INT;
