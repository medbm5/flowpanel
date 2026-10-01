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
