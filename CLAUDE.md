# Flowpanel — conventions for Claude Code sessions

Mini VMS (vendor management system) for temporary staffing with an accountable AI layer.
Mission phases: INTAKE → SOURCING → CONTRACTS → TIMESHEETS → INVOICE → CLOSED, each phase gated.
Demo project, not affiliated with Pixid. **Synthetic data only.**

## Layout
- `backend/` Spring Boot 3.5 (Java 21, Maven wrapper), package-by-feature under `com.flowpanel`:
  `tenant`, `auth`, `mission`, `intake`, `sourcing`, `contract`, `timesheet`, `invoice`, `copilot`, `ai`, `audit`, `metrics`.
- `frontend/` Next.js App Router, TypeScript strict, Tailwind, shadcn/ui, Framer Motion, lucide-react.
- `evals/` Python CLI (`python -m evals run --suite all --base-url ...`) and golden datasets.
- `docs/` architecture, ADRs, deployment, demo script. `PROGRESS.md` is the run log.

## Backend rules
- Records for DTOs, constructor injection only, errors as RFC 7807 `ProblemDetail`.
- A failed gate → `409 Conflict` with the unmet checks. Phase-specific writes outside the current phase → 409.
- Every query scoped by tenant; supplier users additionally scoped by supplier, in the service/repository layer.
  Cross-tenant access returns 404 (never 403) so existence isn't leaked.
- **AI never decides.** LLM extracts, ranks-explains, drafts. Compliance, matching, money (BigDecimal) and transitions
  are deterministic Java with unit tests.
- Every LLM call goes through `AiGateway` (PII masking → `ai_call` row → `audit_event`). Profiles: `mock` (default,
  deterministic, used in tests/CI) and `live` (OpenAI via Spring AI). Model names come from config only.
- Spend protection: `AI_DAILY_BUDGET_USD` (default 2), per-tenant rate limit, `max_tokens` on every call.

## Frontend rules
- Browser calls backend only via the Next rewrite `/api/*` → `BACKEND_URL` (first-party cookie).
- API types generated with `openapi-typescript` (`npm run gen:api`); never hand-write API types.
- Tokens: cobalt `#2F4BD6`, ok `#1F7A55`, warn `#9A6514`, bad `#B4362F`, slate neutrals, "Schibsted Grotesk".
  Light + dark themes, `prefers-reduced-motion` respected, visible focus, responsive to 360px.

## Commands
- `make db-up` local Postgres (pgvector, port 5433) · `make dev` everything · `make test` · `make eval`
- Backend: `cd backend && ./mvnw verify` (Testcontainers needs Docker)
- Frontend: `cd frontend && npm run lint && npm run typecheck && npm run test && npm run build`

## Never
- Commit secrets (use `.env.example`). Skip failing tests. Let the LLM make a compliance or money decision.
