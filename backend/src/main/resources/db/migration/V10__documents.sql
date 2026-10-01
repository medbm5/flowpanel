CREATE TABLE document (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT      NOT NULL REFERENCES tenant (id),
    title        TEXT        NOT NULL,
    source       TEXT        NOT NULL CHECK (source IN ('SEED', 'UPLOAD')),
    content_type TEXT        NOT NULL,
    content      TEXT        NOT NULL,
    content_hash TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, content_hash)
);

-- tenant_id is denormalized on chunks so the vector search and the tenant filter are one SQL query.
CREATE TABLE document_chunk (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT       NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    tenant_id   BIGINT       NOT NULL REFERENCES tenant (id),
    chunk_index INT          NOT NULL,
    heading     TEXT,
    content     TEXT         NOT NULL,
    tokens      INT          NOT NULL,
    model       TEXT         NOT NULL,
    embedding   vector(1536) NOT NULL
);
CREATE INDEX document_chunk_tenant_idx ON document_chunk (tenant_id);
CREATE INDEX document_chunk_embedding_idx ON document_chunk USING hnsw (embedding vector_cosine_ops);
