CREATE TABLE tenant (
    id         BIGSERIAL PRIMARY KEY,
    code       TEXT        NOT NULL UNIQUE,
    name       TEXT        NOT NULL,
    sector     TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE supplier (
    id         BIGSERIAL PRIMARY KEY,
    code       TEXT        NOT NULL UNIQUE,
    name       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The panel: which suppliers a tenant works with.
CREATE TABLE tenant_supplier (
    tenant_id   BIGINT NOT NULL REFERENCES tenant (id),
    supplier_id BIGINT NOT NULL REFERENCES supplier (id),
    PRIMARY KEY (tenant_id, supplier_id)
);

CREATE TABLE app_user (
    id           BIGSERIAL PRIMARY KEY,
    persona      TEXT   NOT NULL UNIQUE,
    display_name TEXT   NOT NULL,
    job_title    TEXT   NOT NULL,
    email        TEXT   NOT NULL UNIQUE,
    role         TEXT   NOT NULL CHECK (role IN ('BUYER', 'SUPPLIER', 'ADMIN')),
    tenant_id    BIGINT REFERENCES tenant (id),
    supplier_id  BIGINT REFERENCES supplier (id),
    CONSTRAINT app_user_scope CHECK (
        (role = 'BUYER' AND tenant_id IS NOT NULL AND supplier_id IS NULL)
        OR (role = 'SUPPLIER' AND supplier_id IS NOT NULL AND tenant_id IS NULL)
        OR (role = 'ADMIN' AND tenant_id IS NULL AND supplier_id IS NULL))
);

CREATE TABLE audit_event (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT REFERENCES tenant (id),
    supplier_id   BIGINT REFERENCES supplier (id),
    mission_id    BIGINT,
    actor_kind    TEXT        NOT NULL CHECK (actor_kind IN ('HUMAN', 'AI', 'SYSTEM')),
    actor_user_id BIGINT REFERENCES app_user (id),
    actor_name    TEXT        NOT NULL,
    action        TEXT        NOT NULL,
    summary       TEXT        NOT NULL,
    details       JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_event_tenant_idx ON audit_event (tenant_id, created_at DESC);
CREATE INDEX audit_event_mission_idx ON audit_event (mission_id, created_at DESC);

-- Reference data (synthetic). Mission data is seeded by the Java demo seeder so it can be reset.
INSERT INTO tenant (id, code, name, sector) VALUES
    (1, 'loginord', 'LogiNord', 'Logistics'),
    (2, 'metalpro', 'MétalPro', 'Manufacturing');

INSERT INTO supplier (id, code, name) VALUES
    (1, 'intersud', 'InterSud Intérim'),
    (2, 'proxi', 'Proxi Staffing'),
    (3, 'atlas', 'Atlas RH');

INSERT INTO tenant_supplier (tenant_id, supplier_id) VALUES
    (1, 1), (1, 2), (1, 3),
    (2, 2), (2, 3);

INSERT INTO app_user (id, persona, display_name, job_title, email, role, tenant_id, supplier_id) VALUES
    (1, 'claire', 'Claire Dubois', 'HR manager, LogiNord', 'claire.dubois@loginord.example', 'BUYER', 1, NULL),
    (2, 'marc', 'Marc Lefèvre', 'Plant HR lead, MétalPro', 'marc.lefevre@metalpro.example', 'BUYER', 2, NULL),
    (3, 'nadia', 'Nadia Benali', 'Account manager, InterSud Intérim', 'nadia.benali@intersud.example', 'SUPPLIER', NULL, 1),
    (4, 'thomas', 'Thomas Girard', 'Account manager, Proxi Staffing', 'thomas.girard@proxi.example', 'SUPPLIER', NULL, 2),
    (5, 'admin', 'Alex Morel', 'Platform admin', 'admin@flowpanel.example', 'ADMIN', NULL, NULL);

SELECT setval('tenant_id_seq', 100);
SELECT setval('supplier_id_seq', 100);
SELECT setval('app_user_id_seq', 100);
