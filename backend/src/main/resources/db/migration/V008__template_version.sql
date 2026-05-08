-- Template version history: one row per published snapshot
CREATE TABLE template_version (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     UUID NOT NULL REFERENCES template_registry(id) ON DELETE CASCADE,
    version_number  INT  NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT now(),
    created_by      UUID REFERENCES users(id),
    comment         TEXT,
    UNIQUE (template_id, version_number)
);

CREATE INDEX idx_template_version_tid ON template_version(template_id, version_number DESC);
