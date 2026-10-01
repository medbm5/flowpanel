-- One row per LLM / embedding call made through the AiGateway.
CREATE TABLE ai_call (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT REFERENCES tenant (id),
    supplier_id        BIGINT REFERENCES supplier (id),
    mission_id         BIGINT,
    feature            TEXT           NOT NULL,
    kind               TEXT           NOT NULL CHECK (kind IN ('STRUCTURED', 'TEXT', 'TOOLS', 'EMBEDDING')),
    profile            TEXT           NOT NULL CHECK (profile IN ('mock', 'live')),
    model              TEXT           NOT NULL,
    input_tokens       INT            NOT NULL DEFAULT 0,
    output_tokens      INT            NOT NULL DEFAULT 0,
    latency_ms         BIGINT         NOT NULL DEFAULT 0,
    estimated_cost_usd NUMERIC(12, 6) NOT NULL DEFAULT 0,
    status             TEXT           NOT NULL CHECK (status IN ('OK', 'INVALID_OUTPUT', 'ERROR', 'BUDGET_EXCEEDED', 'RATE_LIMITED')),
    attempt            INT            NOT NULL DEFAULT 1,
    error              TEXT,
    prompt_hash        TEXT           NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ai_call_created_idx ON ai_call (created_at DESC);
CREATE INDEX ai_call_tenant_idx ON ai_call (tenant_id, created_at DESC);
CREATE INDEX ai_call_mission_idx ON ai_call (mission_id);

-- Embeddings are computed once per (content hash, model) and reused.
CREATE TABLE embedding_cache (
    content_hash TEXT        NOT NULL,
    model        TEXT        NOT NULL,
    embedding    vector(1536) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (content_hash, model)
);

-- People mentioned in tenant emails and documents (site managers, replaced employees...). Their names are masked
-- before any text reaches an LLM provider, like worker and user names.
CREATE TABLE known_contact (
    id        BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    full_name TEXT   NOT NULL,
    UNIQUE (tenant_id, full_name)
);

INSERT INTO known_contact (tenant_id, full_name) VALUES
    (1, 'Paul Vasseur'),
    (1, 'Sarah Lemaire'),
    (1, 'Nathalie Roche'),
    (1, 'Julie Perrin'),
    (2, 'Isabelle Caron');
