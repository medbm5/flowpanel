-- AI-extracted order draft, one per mission. fields: [{name, value, aiValue, confidence, errors, needsReview, confirmed, corrected}]
CREATE TABLE intake_draft (
    mission_id   BIGINT PRIMARY KEY REFERENCES mission (id) ON DELETE CASCADE,
    ai_call_id   BIGINT,
    fields       JSONB       NOT NULL,
    extracted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
