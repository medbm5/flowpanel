CREATE TABLE invoice (
    id                     BIGSERIAL PRIMARY KEY,
    mission_id             BIGINT        NOT NULL REFERENCES mission (id) ON DELETE CASCADE,
    supplier_id            BIGINT        NOT NULL REFERENCES supplier (id),
    ref                    TEXT          NOT NULL UNIQUE,
    pdf                    BYTEA         NOT NULL,
    extracted_text         TEXT          NOT NULL,
    extraction             JSONB,
    extraction_ai_call_id  BIGINT,
    match_result           JSONB,
    status                 TEXT          NOT NULL CHECK (status IN ('RECEIVED', 'MISMATCH', 'MATCHED', 'APPROVED')),
    message_draft          TEXT,
    message_ai_call_id     BIGINT,
    credit_note_ref        TEXT,
    credit_note_amount     NUMERIC(12, 2),
    credit_note            JSONB,
    approved_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (mission_id, supplier_id)
);
CREATE INDEX invoice_mission_idx ON invoice (mission_id);
