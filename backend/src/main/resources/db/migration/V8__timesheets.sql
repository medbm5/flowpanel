-- Weekly timesheets, submitted by the supplier (simulated). daily_hours: 7 numbers, Monday..Sunday.
CREATE TABLE timesheet (
    id               BIGSERIAL PRIMARY KEY,
    mission_id       BIGINT        NOT NULL REFERENCES mission (id) ON DELETE CASCADE,
    contract_id      BIGINT        NOT NULL REFERENCES contract (id) ON DELETE CASCADE,
    worker_id        BIGINT        NOT NULL REFERENCES worker (id),
    supplier_id      BIGINT        NOT NULL REFERENCES supplier (id),
    week_start       DATE          NOT NULL,
    daily_hours      JSONB         NOT NULL,
    contracted_hours NUMERIC(6, 2) NOT NULL,
    status           TEXT          NOT NULL CHECK (status IN ('SUBMITTED', 'CORRECTED', 'APPROVED')),
    approved_hours   NUMERIC(6, 2),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (contract_id, week_start)
);
CREATE INDEX timesheet_mission_idx ON timesheet (mission_id);

CREATE TABLE timesheet_anomaly (
    id                     BIGSERIAL PRIMARY KEY,
    mission_id             BIGINT        NOT NULL REFERENCES mission (id) ON DELETE CASCADE,
    timesheet_id           BIGINT        NOT NULL REFERENCES timesheet (id) ON DELETE CASCADE,
    rule_id                TEXT          NOT NULL,
    message                TEXT          NOT NULL,
    expected_hours         NUMERIC(6, 2),
    actual_hours           NUMERIC(6, 2),
    flagged_days           JSONB         NOT NULL DEFAULT '[]'::jsonb,
    explanation            TEXT,
    explanation_ai_call_id BIGINT,
    status                 TEXT          NOT NULL CHECK (status IN ('OPEN', 'RESOLVED')),
    resolution             TEXT CHECK (resolution IN ('APPROVE_OVERTIME', 'RETURN_TO_SUPPLIER')),
    resolved_by            BIGINT REFERENCES app_user (id),
    resolved_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX timesheet_anomaly_mission_idx ON timesheet_anomaly (mission_id);

CREATE TABLE timesheet_check_run (
    mission_id BIGINT PRIMARY KEY REFERENCES mission (id) ON DELETE CASCADE,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    anomalies  INT         NOT NULL
);
