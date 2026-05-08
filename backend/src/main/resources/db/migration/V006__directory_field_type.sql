ALTER TABLE directory_schema_field
    ADD COLUMN field_type    VARCHAR(50) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN field_options JSONB;
