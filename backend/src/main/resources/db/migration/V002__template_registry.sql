CREATE TABLE template_registry (
    id            UUID PRIMARY KEY,
    code          VARCHAR(100) UNIQUE,
    name          VARCHAR(500) NOT NULL,
    description   TEXT,
    category      VARCHAR(100),
    version       INT DEFAULT 1,
    is_published  BOOLEAN DEFAULT FALSE,
    owner_id      UUID REFERENCES users(id),
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now()
);
