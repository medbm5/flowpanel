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
