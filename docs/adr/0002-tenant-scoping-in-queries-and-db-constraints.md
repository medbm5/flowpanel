# ADR 0002 — Tenant scoping in queries and invariants in database constraints

- Status: accepted
- Date: 2026-10-01

## Context

Several clients (tenants) and several staffing suppliers share the platform. A buyer must never see another tenant's data, a supplier
must never see a competitor's proposals or rates, and a worker must never be placed on two overlapping missions — even under
concurrent clicks or a prompt-injected copilot.

## Decision

- Scope comes **only from the signed session** (`RequestContext`: tenantId / supplierId / role), never from request parameters.
- Every query on tenant data includes the scope **in the repository / SQL layer** (`findByIdAndTenantId`, `where m.tenant_id = ?`,
  supplier filters for supplier users). Phase writes go through `requireCurrentPhase`, which loads the mission by id **and** tenant.
- Out-of-scope resources answer **404** (indistinguishable from a non-existent id), not 403.
- Copilot tools read the same scope; model-chosen arguments can only narrow results.
- Vector search filters `tenant_id` in the same statement as the similarity ordering (no post-filtering in Java).
- Business invariants that concurrency could break are enforced by PostgreSQL: the double-booking rule is an
  `EXCLUDE USING gist (worker_id WITH =, period WITH &&)` constraint (btree_gist); the service maps the violation to a 409 that names the
  conflicting mission. Role/scope consistency of users is a `CHECK` constraint.

## Alternatives considered

- Row-level security (Postgres RLS): strong, but needs a per-request `SET` and makes the demo harder to follow; a good next step at scale.
- Schema- or database-per-tenant: heavy for a demo, and suppliers span tenants.

## Consequences

- Isolation is tested end to end (`IsolationIT`, `MissionIT`, `SourcingIT`, `CopilotIT`, `InvoiceIT`), including a concurrent double
  booking test and a cross-tenant RAG test with near-identical documents.
- Every new query must carry the scope; code review and the integration tests are the guard.
