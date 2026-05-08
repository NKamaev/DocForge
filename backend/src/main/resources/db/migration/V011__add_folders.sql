CREATE TABLE folders (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    color       VARCHAR(50),
    owner_id    UUID REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE document_registry
    ADD COLUMN folder_id UUID REFERENCES folders(id) ON DELETE SET NULL;

ALTER TABLE template_registry
    ADD COLUMN default_folder_id UUID;
