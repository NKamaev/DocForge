CREATE TABLE audit_log (
    id            BIGSERIAL PRIMARY KEY,
    user_id       UUID REFERENCES users(id),
    entity_type   VARCHAR(50) NOT NULL,
    entity_id     UUID NOT NULL,
    action        VARCHAR(50) NOT NULL,
    details       JSONB,
    created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_user   ON audit_log(user_id, created_at);
