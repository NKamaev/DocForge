-- Справочники: метаданные (название, владелец)
CREATE TABLE directory_registry (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(500) NOT NULL,
    owner_id    UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now()
);

-- Схема полей справочника
CREATE TABLE directory_schema_field (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id   UUID NOT NULL REFERENCES directory_registry(id) ON DELETE CASCADE,
    field_key      VARCHAR(100) NOT NULL,
    label          VARCHAR(500) NOT NULL,
    display_order  INT NOT NULL DEFAULT 0,
    UNIQUE (directory_id, field_key)
);
