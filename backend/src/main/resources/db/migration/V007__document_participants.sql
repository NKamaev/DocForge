CREATE TABLE document_participants (
    document_id UUID REFERENCES document_registry(id) ON DELETE CASCADE,
    user_id     UUID REFERENCES users(id),
    role        VARCHAR(50) DEFAULT 'EDITOR',
    joined_at   TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (document_id, user_id)
);
