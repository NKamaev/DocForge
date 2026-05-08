-- 1. Таблица ролей на папки
CREATE TABLE folder_members (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    folder_id   UUID NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL CHECK (role IN ('VIEWER','EDITOR','MANAGER','OWNER')),
    granted_by  UUID REFERENCES users(id),
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(folder_id, user_id)
);
CREATE INDEX idx_folder_members_user ON folder_members(user_id);

-- 2. Переименовать document_participants → document_members, добавить колонки
ALTER TABLE document_participants RENAME TO document_members;
ALTER TABLE document_members ADD COLUMN id UUID DEFAULT gen_random_uuid();
ALTER TABLE document_members ADD COLUMN granted_by UUID REFERENCES users(id);
ALTER TABLE document_members ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'DIRECT'
    CHECK (source IN ('DIRECT','FOLDER_INHERIT'));
ALTER TABLE document_members RENAME COLUMN joined_at TO granted_at;
ALTER TABLE document_members ALTER COLUMN role SET DEFAULT 'VIEWER';
