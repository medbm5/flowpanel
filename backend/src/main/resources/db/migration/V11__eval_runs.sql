-- Results of the eval suite (posted by evals/ after each run), shown on the admin dashboard.
CREATE TABLE eval_run (
    id         BIGSERIAL PRIMARY KEY,
    git_sha    TEXT        NOT NULL,
    profile    TEXT        NOT NULL,
    passed     BOOLEAN     NOT NULL,
    metrics    JSONB       NOT NULL,
    thresholds JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX eval_run_created_idx ON eval_run (created_at DESC);
