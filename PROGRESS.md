# Progress log

Run log for the BUILD_PLAN.md slices. One entry per slice.

## Blocked / follow-ups

_None yet._

## Slices

### Slice 0 — Repository bootstrap
- Built: monorepo layout (`backend/`, `frontend/`, `evals/`, `docs/adr/`), `.gitignore`, `.editorconfig`, `.gitattributes`,
  `docker-compose.yml` (pgvector/pgvector:pg16), `Makefile` (dev, test, eval, db-up, db-reset, seed), `CLAUDE.md`, README skeleton, `.env.example`.
- Deviations: Postgres is published on host port **5433** (not 5432) to avoid clashing with a native Postgres install.
  Python available locally is 3.11 (plan says 3.12); the eval CLI targets 3.11+ and CI uses 3.12.
  Default chat model `gpt-4o-mini` (cheap tier that supports JSON-schema structured outputs and temperature; checked on OpenAI pricing page).

### Slice 1 — Backend skeleton
- Built: Spring Boot 3.5.16 app (Web, JPA, Security, Validation, Flyway, Actuator, PostgreSQL, springdoc 2.9.1) with Maven wrapper (Maven 3.9.16),
  `V1__extensions.sql` (vector, btree_gist), RFC 7807 `GlobalExceptionHandler`, public `/actuator/health`, `/v3/api-docs`, `/swagger-ui`,
  profiles `local` / `test` / `prod`, env-var config, `AbstractIntegrationTest` (singleton pgvector Testcontainer + MockMvc), `SmokeIT`.
- Versions checked on Maven Central: Spring Boot 3.5.16 is the latest 3.x (4.x exists but the plan asks for 3.x); springdoc 2.9.1 is built on Boot 3.5.16.
- Deviations: integration tests use the `*IT` suffix and run in failsafe during `verify`. The `local` profile imports the repo-root `.env` (gitignored).

### Slice 2 — Multi-tenancy, multi-party access and demo auth
- Built: `tenant`, `supplier`, `tenant_supplier` (panel), `app_user` (role-scope CHECK constraint), `audit_event`; reference data in `V2`.
  Demo personas: `claire` (LogiNord buyer), `marc` (MétalPro buyer), `nadia` (InterSud Intérim), `thomas` (Proxi Staffing), `admin`.
  `POST /auth/demo-login` (HS256 JWT via jjwt 0.13.0 in an httpOnly SameSite=Lax cookie), `POST /auth/logout`, `GET /auth/me`, `GET /auth/personas`.
  `RequestContext` (tenantId / supplierId / role / userId from the JWT), `AuditService`, scoped `TenantService` (`/tenants/{id}`, `/suppliers`, `/suppliers/{id}`).
- Tests: `AuthIT`, `IsolationIT` (tenant boundary and supplier boundary, both 404).
- Deviations: added a second supplier persona (`thomas`, Proxi) so supplier-vs-supplier isolation can be demoed from both sides.
  Tenants/suppliers/users are Flyway reference data; mission data is seeded by a Java seeder (Slice 3) so it can be reset.

### Slice 3 — Mission domain and phase state machine
- Built: `worker` (15 synthetic workers), `request_template` (3 templates), `mission` (ref `ORD-<year>-NNNN`), `phase_completion`, `artifact` (jsonb payload).
  `Phase` enum with an explicit allowed-transitions map; `GateValidator` (one per phase) + `GateRegistry` + `GateEvaluation`
  (checks, next action, needs-review computed server-side). Endpoints: `GET /missions?status=all|open|review|closed`, `POST /missions`
  (template or raw email), `GET /missions/{id}`, `POST /missions/{id}/phases/{phase}/finalize` (409 + `unmetChecks`, or 409 + `currentPhase`),
  `GET /missions/{id}/audit`, `GET /request-templates`, `POST /admin/demo/reset`. `MissionService.requireCurrentPhase` is the 409 guard
  every phase-specific write goes through (row lock + phase check). Phase lifecycle is published as in-transaction events
  (`PhaseFinalized`, `PhaseEntered`) so feature modules open/close their phase without circular dependencies.
- `DemoSeeder` drives the seeded missions through the real services and gates (ORD-0142 → TIMESHEETS, ORD-0147 → SOURCING,
  plus MétalPro ORD-0145 at INTAKE for isolation demos). It is extended by every later slice.
- Tests: `PhaseTransitionTest` (all 36 from/to pairs), `MissionIT` (two missions advancing independently, gate and phase 409s,
  tenant isolation on missions, filters), `DemoResetIT`.
- Deviations: `GateCheck` carries `id`, `action` and `needsReview` in addition to `label`/`passed`, so the UI can show the next
  action and the "Needs review" tag without extra logic. Workers and templates are Flyway reference data; only missions are reset.
  Gate validators start as artifact-based checks and are replaced with the real phase rules in Slices 5–9.
