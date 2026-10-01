-- Which panel suppliers received the order.
CREATE TABLE mission_supplier (
    mission_id   BIGINT      NOT NULL REFERENCES mission (id) ON DELETE CASCADE,
    supplier_id  BIGINT      NOT NULL REFERENCES supplier (id),
    published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (mission_id, supplier_id)
);

-- Supplier proposals, ranked deterministically. explanation: [{ok, text}] built from rule and score inputs.
CREATE TABLE candidate (
    id                 BIGSERIAL PRIMARY KEY,
    mission_id         BIGINT        NOT NULL REFERENCES mission (id) ON DELETE CASCADE,
    worker_id          BIGINT        NOT NULL REFERENCES worker (id),
    supplier_id        BIGINT        NOT NULL REFERENCES supplier (id),
    rank               INT,
    score              NUMERIC(5, 1) NOT NULL DEFAULT 0,
    similarity         NUMERIC(6, 4) NOT NULL DEFAULT 0,
    distance_km        NUMERIC(7, 1),
    eligible           BOOLEAN       NOT NULL,
    exclusion_reasons  JSONB         NOT NULL DEFAULT '[]'::jsonb,
    explanation        JSONB         NOT NULL DEFAULT '[]'::jsonb,
    summary            TEXT,
    summary_ai_call_id BIGINT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (mission_id, worker_id)
);
CREATE INDEX candidate_mission_idx ON candidate (mission_id);

-- A worker can never be placed on two overlapping missions: enforced by the database, not only by the service.
CREATE TABLE placement (
    id           BIGSERIAL PRIMARY KEY,
    mission_id   BIGINT      NOT NULL REFERENCES mission (id) ON DELETE CASCADE,
    worker_id    BIGINT      NOT NULL REFERENCES worker (id),
    supplier_id  BIGINT      NOT NULL REFERENCES supplier (id),
    candidate_id BIGINT      NOT NULL REFERENCES candidate (id) ON DELETE CASCADE,
    period       DATERANGE   NOT NULL,
    created_by   BIGINT REFERENCES app_user (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (mission_id, worker_id),
    CONSTRAINT placement_no_double_booking EXCLUDE USING gist (worker_id WITH =, period WITH &&)
);
CREATE INDEX placement_mission_idx ON placement (mission_id);
