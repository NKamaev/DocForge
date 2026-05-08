-- Link documents to specific template snapshots
ALTER TABLE document_registry
    ADD COLUMN template_snapshot_id UUID,
    ADD COLUMN template_version_number INT;
