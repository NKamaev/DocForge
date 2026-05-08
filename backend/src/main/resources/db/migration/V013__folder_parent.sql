ALTER TABLE folders ADD COLUMN parent_id UUID REFERENCES folders(id) ON DELETE CASCADE;
CREATE INDEX idx_folders_parent ON folders(parent_id);
