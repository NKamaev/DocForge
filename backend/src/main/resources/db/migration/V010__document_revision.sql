-- Document revision history: metadata in PostgreSQL, body in MongoDB
CREATE TABLE document_revision (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES document_registry(id) ON DELETE CASCADE,
    revision_number INT NOT NULL,
    saved_by        UUID REFERENCES users(id),
    saved_at        TIMESTAMPTZ DEFAULT now(),
    comment         TEXT,
    UNIQUE (document_id, revision_number)
);

CREATE INDEX idx_doc_revision ON document_revision(document_id, revision_number DESC);
