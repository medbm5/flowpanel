# Flowpanel — Build plan for Claude Code

A mini VMS (vendor management system) for temporary staffing with an AI layer at every phase of a mission:
Intake → Sourcing → Contracts → Timesheets → Invoice → Closed. Several missions run in parallel, each with its own
phase-gated workflow. Includes a public marketing landing page.

> Demo project built for a job application. Not affiliated with Pixid. All data is synthetic.

---

## How to run this plan

1. Create an empty folder, `git init`, and put this file at the root as `BUILD_PLAN.md`.
2. Start Claude Code in that folder and paste the **master prompt** below.

### Master prompt (paste this into Claude Code)

```
Read BUILD_PLAN.md fully before writing any code. Then execute every slice in order, from Slice 0 to the last slice,
in a single run, without asking me for confirmation between slices.

For each slice:
1. Implement everything listed under "Tasks".
2. Run every command under "Verify" and fix the code until all of them pass.
3. Check every item under "Done when".
4. Stage all changes and commit with the exact commit message given for the slice.
5. Append a short entry to PROGRESS.md (slice number, what was built, any deviation from the plan and why).
6. Move on to the next slice immediately.

Rules:
- Follow the "Global rules" section of BUILD_PLAN.md at all times.
- Before using any library, check its current stable version (Maven Central, npm, or official docs) instead of guessing.
- Never commit secrets. Use .env.example files.
- If something is genuinely blocked (missing API key, external service down), implement it behind the mock profile,
  note it in PROGRESS.md under "Blocked / follow-ups", and continue. Do not stop the run.
- Never skip a failing test to make a slice pass. Fix the cause.
- At the end, print a summary: slices completed, commits created, anything left in "Blocked / follow-ups",
  and the exact commands to run the app locally.
```

Tip: run Claude Code with auto-accept for edits, and allow `mvn`, `npm`, `npx`, `docker`, `python`, `pytest` and `git`
commands so the run isn't interrupted. Only use a fully unrestricted mode inside a disposable container or VM.

---

## Global rules

### Stack
| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 3.x (latest stable), Maven, Spring Web, Spring Data JPA, Spring Security, Flyway, springdoc-openapi |
| AI | Spring AI (latest stable 1.x) with the OpenAI starter. Chat: a small, cost-efficient OpenAI model (check OpenAI's current model list, e.g. a `mini` tier). Embeddings: `text-embedding-3-small` (1536 dimensions). Model names in config, never hard-coded |
| Database | PostgreSQL 16 with `pgvector` and `btree_gist` extensions (Neon free tier in prod, Docker locally) |
| Frontend | Next.js (App Router, latest stable), TypeScript strict, Tailwind CSS, shadcn/ui, Framer Motion, lucide-react |
| Evals | Python 3.12, a small CLI under `/evals` |
| Tests | JUnit 5, AssertJ, Testcontainers (Postgres + pgvector image), Vitest + Testing Library, Playwright (smoke) |
| CI/CD | GitHub Actions. Backend on Render (Docker, free). Frontend on Vercel (free) |

### Repository layout
```
/backend     Spring Boot app
/frontend    Next.js app (landing page + product app)
/evals       Python eval runner and golden datasets
/docs        architecture.md, adr/, demo-script.md
docker-compose.yml   local Postgres (pgvector/pgvector:pg16)
Makefile             dev, test, eval, seed shortcuts
CLAUDE.md            conventions summary for future sessions
PROGRESS.md          run log
```

### Backend conventions
- Package by feature: `tenant`, `auth`, `mission`, `intake`, `sourcing`, `contract`, `timesheet`, `invoice`, `copilot`, `ai`, `audit`, `metrics`.
- Java records for DTOs. Constructor injection only. No field injection.
- Errors as RFC 7807 `ProblemDetail`. A failed gate returns `409 Conflict` with the list of unmet checks.
- Every query is scoped by tenant, and supplier-role queries are also scoped by supplier. Scoping lives in the service/repository layer, never only in the UI.
- **AI never makes decisions.** The LLM extracts, ranks, explains, and drafts. Compliance checks, matching, money calculations and phase transitions are deterministic Java code with unit tests.
- Every LLM call goes through `AiGateway`, which applies PII masking, writes an `ai_call` record (feature, tenant, model, tokens, latency, estimated cost, status) and an audit entry.
- Two AI profiles: `mock` (default, deterministic, no API key needed, used in tests and CI) and `live` (OpenAI chat + embeddings).
- Use OpenAI structured outputs (JSON schema response format) for every structured extraction, through Spring AI's `BeanOutputConverter` / entity mapping.
- Spend protection: a configurable daily budget (`AI_DAILY_BUDGET_USD`, default 2) and a per-tenant request rate limit. When the budget is reached, the gateway refuses live calls with a clear error and the UI shows it. Cheap model by default, `max_tokens` set on every call.

### Frontend conventions
- Design tokens taken from the prototype: cobalt accent `#2F4BD6`, ok `#1F7A55`, warn `#9A6514`, bad `#B4362F`, neutral slate surfaces, font "Schibsted Grotesk" with a system fallback. Full light and dark themes.
- The browser calls the backend only through a Next.js rewrite `/api/*` → `BACKEND_URL`, so the auth cookie stays first-party.
- API types generated from the backend OpenAPI spec with `openapi-typescript`. No hand-written API types.
- Respect `prefers-reduced-motion` everywhere. Visible keyboard focus. Responsive down to 360px.

### Data and compliance
- Synthetic data only: tenants LogiNord (logistics) and MétalPro (manufacturing); suppliers InterSud Intérim, Proxi Staffing, Atlas RH; fictional workers.
- PII (names, emails, phone numbers) masked before any text is sent to an LLM provider, restored afterward.
- Every AI suggestion and every human decision is written to `audit_event`.

---

## Slice 0 — Repository bootstrap

**Tasks**
- Create the layout above, `.gitignore` (Java, Node, Python, env files), `.editorconfig`, `docker-compose.yml` with `pgvector/pgvector:pg16`, a `Makefile` with `dev`, `test`, `eval`, `db-up`, `db-reset` targets.
- Write `CLAUDE.md` summarizing the global rules, and an empty `PROGRESS.md` with a "Blocked / follow-ups" section.
- Write a README skeleton: project pitch, stack, how to run locally (to be completed in the last slice).

**Verify**: `docker compose up -d && docker compose ps`

**Done when**: Postgres is reachable locally; files above exist.

**Commit**: `chore: bootstrap monorepo with docker-compose, Makefile and conventions`

---

## Slice 1 — Backend skeleton

**Tasks**
- Generate the Spring Boot project in `/backend` (Web, JPA, Security, Validation, Flyway, Actuator, PostgreSQL driver, springdoc).
- Flyway `V1__extensions.sql`: `CREATE EXTENSION IF NOT EXISTS vector; CREATE EXTENSION IF NOT EXISTS btree_gist;`
- Global exception handler returning `ProblemDetail`. `/actuator/health` public. OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui`.
- Profiles: `local`, `test`, `prod`. Config via env vars (`DATABASE_URL`, `AI_PROFILE`, `OPENAI_API_KEY`, `OPENAI_CHAT_MODEL`, `OPENAI_EMBEDDING_MODEL`, `AI_DAILY_BUDGET_USD`, `JWT_SECRET`, `CORS_ORIGINS`).
- Testcontainers base test class reused by all integration tests.

**Verify**: `cd backend && ./mvnw verify`

**Done when**: app starts against local Postgres, health returns UP, a smoke integration test passes.

**Commit**: `feat(backend): Spring Boot skeleton with Flyway, pgvector, OpenAPI and Testcontainers`

---

## Slice 2 — Multi-tenancy, multi-party access and demo auth

**Tasks**
- Tables: `tenant`, `supplier`, `tenant_supplier` (the panel), `app_user` (role `BUYER`, `SUPPLIER`, `ADMIN`; buyers belong to a tenant, supplier users belong to a supplier).
- Demo login `POST /auth/demo-login` with a persona id (Claire at LogiNord, Marc at MétalPro, InterSud supplier user, admin). Issues a signed JWT in an httpOnly cookie. `POST /auth/logout`, `GET /auth/me`.
- `RequestContext` populated from the JWT: tenantId, supplierId (if any), role, userId.
- `audit_event` table and `AuditService`.
- Integration tests proving: a LogiNord buyer can't read a MétalPro resource (404, not 403, to avoid leaking existence); a supplier user can't read another supplier's data.

**Verify**: `cd backend && ./mvnw verify`

**Done when**: isolation tests pass for both tenant and supplier boundaries.

**Commit**: `feat(auth): demo login, tenant and supplier scoping, audit events`

---

## Slice 3 — Mission domain and phase state machine

**Tasks**
- Tables: `mission` (ref like `ORD-2026-0142`, tenant, template fields, `phase`, timestamps), `phase_completion` (mission, phase, finalized_at, finalized_by), `artifact` (mission, phase, type, ref, status, payload jsonb).
- `Phase` enum: INTAKE, SOURCING, CONTRACTS, TIMESHEETS, INVOICE, CLOSED, with an explicit allowed-transitions map.
- `GateValidator` interface with one implementation per phase returning a list of `GateCheck(label, passed)`.
- Endpoints: `GET /missions?status=open|review|closed`, `POST /missions` (from a request template or raw email text), `GET /missions/{id}` (phase, gate checks, next action, artifacts), `POST /missions/{id}/phases/{phase}/finalize` (409 with unmet checks if not ready, or if the phase is not the current one).
- Every phase-specific write endpoint rejects calls when its phase is not the current phase (409).
- "Next action" and "needs review" computed server-side.
- Seed data (Flyway `R__seed.sql` or a `local`/`prod` seeder): 2 tenants, 3 suppliers, ~15 workers with skills, certifications, location and availability; 3 request templates (forklift operators Lille, order pickers Roubaix, office assistant Paris); 2 pre-advanced missions for LogiNord (one at TIMESHEETS, one at SOURCING).
- `POST /admin/demo/reset` re-seeds the demo (admin only).

**Verify**: `cd backend && ./mvnw verify`

**Done when**: unit tests cover every transition (allowed and rejected); integration test shows two missions advancing independently.

**Commit**: `feat(mission): phase state machine with gate validators, artifacts and demo seed`

---

## Slice 4 — AI gateway, PII masking and AI call metrics

**Tasks**
- `AiGateway` interface: `structured(prompt, Class<T>)`, `text(prompt)`, `embed(texts)`, `withTools(prompt, tools)`.
- `SpringAiGateway` (`live` profile): chat and embeddings through Spring AI's OpenAI client, models from config. Structured outputs with JSON schema. Daily budget check before each call, computed from `ai_call` cost records; per-tenant rate limit.
- `MockAiGateway` (`mock` profile, default): deterministic outputs keyed by prompt template id, deterministic hash-based embeddings (same 1536 dimensions as the live model, so the schema is identical), simulated latency and token counts.
- Price table in config for the chosen chat and embedding models (check OpenAI's current pricing page) to compute estimated cost per call.
- Embedding cache: store embeddings for seeded workers and documents once; never re-embed unchanged content (content hash).
- `PiiMasker`: regex for emails and phones, plus names from the tenant's known workers and contacts. Replaces with tokens `[PERSON_1]`, restores after the call. Unit tests with round-trip cases.
- `ai_call` table and recording (feature, mission, tenant, model, input/output tokens, latency ms, estimated cost from a per-model price table in config, status, masked prompt hash).
- Retry once on invalid structured output with the validation error appended; then fail gracefully with a typed error the UI can show.

**Verify**: `cd backend && ./mvnw verify`

**Done when**: every AI call in tests goes through the gateway and produces an `ai_call` row and an audit event; PII never appears in the prompt sent to the provider (assert in tests).

**Commit**: `feat(ai): AI gateway with mock and live profiles, PII masking and call metrics`

---

## Slice 5 — Intake phase

**Tasks**
- `POST /missions/{id}/intake/extract`: LLM structured output into `OrderDraft` (position, quantity, site, start, end, schedule, target rate, legal reason, replaced employee if relevant) with a confidence per field.
- Server-side validation (dates coherent, quantity > 0, rate > 0, reason in the allowed list). Fields that fail validation or have confidence below a threshold are marked `needsReview`.
- `POST /missions/{id}/intake/fields/{field}/confirm` (optionally with a corrected value).
- Gate: draft extracted and no field left in `needsReview`. Artifact: Order (draft → confirmed on finalize).
- Mock responses for the three templates, including one low-confidence field each.

**Verify**: `cd backend && ./mvnw verify`

**Done when**: tests cover valid extraction, invalid output + retry, low-confidence flow, gate pass and fail.

**Commit**: `feat(intake): email-to-order extraction with validation, confidence and field review`

---

## Slice 6 — Sourcing phase with matching and double-booking protection

**Tasks**
- `POST /missions/{id}/sourcing/publish`: marks the order as sent to panel suppliers and creates simulated supplier proposals from the seeded worker pool.
- Ranking in two stages: (1) hard rules exclude candidates (missing certification, unavailable for the full period, already placed on an overlapping mission); (2) score = weighted embedding similarity + distance + experience. Explanations (✓/✗ list) are generated from the rule and score inputs, not by the LLM. Optional one-sentence LLM summary.
- `placement` table (worker, mission, period `daterange`) with a Postgres exclusion constraint: `EXCLUDE USING gist (worker_id WITH =, period WITH &&)`. Selection happens in a transaction; a constraint violation returns 409 "already placed on ORD-xxxx".
- `POST /missions/{id}/sourcing/select/{candidateId}`, `DELETE` to unselect. Cap at the order quantity.
- Gate: published and positions filled. Artifact: Shortlist.
- Supplier users can see only the orders published to their supplier and their own proposals.

**Verify**: `cd backend && ./mvnw verify`

**Done when**: a concurrency test with two parallel selections of the same worker on overlapping missions results in exactly one success.

**Commit**: `feat(sourcing): explainable candidate ranking and DB-enforced double-booking protection`

---

## Slice 7 — Contracts phase

**Tasks**
- `POST /missions/{id}/contracts/generate`: one contract per placement (ref `CT-<num>-0N`), content drafted by the LLM from the order and the placement, structured fields stored.
- `ContractRulesEngine` (pure Java): rate matches order, legal reason present, required certificate attached, contract dates within the order period. Seed one blocking issue (end date after the order end) on the first contract.
- `POST /contracts/{id}/fix/{ruleId}` for auto-fixable issues; `POST /missions/{id}/contracts/sign` (simulated e-signature by both parties).
- Gate: generated, no blocking issue, all signed. Artifacts: Contracts.

**Verify**: `cd backend && ./mvnw verify`

**Done when**: rules engine has one unit test per rule (pass and fail).

**Commit**: `feat(contracts): contract generation with deterministic compliance rules and simulated signature`

---

## Slice 8 — Timesheets phase

**Tasks**
- Generate weekly timesheets for each placement when the phase opens (supplier-submitted in the story). Seed one anomaly: a week with 41 h vs 35 h contracted, no overtime agreed.
- `TimesheetRulesEngine`: contracted hours, overtime allowed or not, hours per day limit.
- `POST /missions/{id}/timesheets/check` returns anomalies; the LLM writes a short explanation for each anomaly from the daily breakdown.
- `POST /anomalies/{id}/resolve` with `APPROVE_OVERTIME` or `RETURN_TO_SUPPLIER` (the latter simulates a corrected timesheet). `POST /missions/{id}/timesheets/approve`.
- Gate: checks run, anomalies resolved, approved. Artifact: Timesheets.

**Verify**: `cd backend && ./mvnw verify`

**Done when**: approved hours are computed in Java and covered by tests for both resolution paths.

**Commit**: `feat(timesheets): weekly hours with rule-based anomaly detection and AI explanations`

---

## Slice 9 — Invoice phase and three-way match

**Tasks**
- Generate a synthetic supplier invoice PDF with PDFBox that overbills a few hours. Store it as an artifact.
- `POST /missions/{id}/invoice/receive`: extract text with PDFBox, LLM structured output into `InvoiceLines`, validation.
- `ThreeWayMatcher` (pure Java, `BigDecimal`): compares contract rate, approved hours and invoice lines; returns line-by-line results.
- On mismatch, the LLM drafts a French message to the supplier requesting a credit note. `POST /missions/{id}/invoice/request-credit-note` simulates the credit note; the match passes. `POST /missions/{id}/invoice/approve`.
- Finalizing INVOICE moves the mission to CLOSED and computes a summary (workers placed, hours approved, amount approved, overbilling avoided, AI steps reviewed).

**Verify**: `cd backend && ./mvnw verify`

**Done when**: matcher tests cover exact match, hour mismatch, rate mismatch and rounding.

**Commit**: `feat(invoice): PDF invoice extraction, three-way match and credit note flow`

---

## Slice 10 — Copilot: tool calling and document Q&A with citations (RAG)

**Tasks**
- Tenant documents: seed 3–4 synthetic policy documents per tenant (site safety rules, night work policy, supplier panel agreement). `POST /documents` to upload PDF or Markdown.
- Ingestion: text extraction, chunking (~500 tokens, overlap ~60, split on headings first), embeddings, `document_chunk` table with `tenant_id` and `vector` column, HNSW index.
- Retrieval with `WHERE tenant_id = :tenant` in the same SQL query as the vector search. Answers must cite chunks by number; the backend verifies that every citation refers to a retrieved chunk and drops the answer to "not found in your documents" if none is valid.
- Tools exposed to the LLM (all scoped to the current tenant/supplier): `listMissions`, `getMissionStatus`, `getSpendBySupplier`, `listOpenAnomalies`, `listContractsEndingBefore`, `searchDocuments`.
- `POST /copilot/ask` returns answer, citations, and the list of tool calls made (for the UI trace).
- Test: a supplier asking for another supplier's rates gets no data, whatever the prompt says.

**Verify**: `cd backend && ./mvnw verify`

**Done when**: tenant isolation of retrieval is proven by an integration test with near-identical documents in both tenants.

**Commit**: `feat(copilot): tenant-scoped RAG with verified citations and tool-calling assistant`

---

## Slice 11 — Frontend foundation

**Tasks**
- Create the Next.js app in `/frontend` (TypeScript strict, ESLint, Tailwind, shadcn/ui). Fonts via `next/font`.
- Design tokens as CSS variables (light and dark) matching the global rules. Base components: Button, Badge, Card, Dialog, Tabs, Toast, Accordion.
- Rewrite `/api/:path*` → `${BACKEND_URL}/:path*`. Typed API client generated from OpenAPI (`npm run gen:api`).
- Routes: `/` (landing, built in Slice 15), `/login` (persona picker), `/app` (product, protected by middleware checking the auth cookie).
- App shell: top bar with tenant name, user, theme toggle, logout.

**Verify**: `cd frontend && npm run lint && npm run typecheck && npm run build`

**Done when**: persona login works against the local backend and lands on `/app`.

**Commit**: `feat(frontend): Next.js foundation with design tokens, typed API client and demo login`

---

## Slice 12 — Missions board

**Tasks**
- `/app` board: one row per mission with ref, title, site, positions filled, 6-segment phase progress, next action, "Needs review" tag. Filters: All, In progress, Needs review, Closed.
- "New mission" dialog: choose a request template or paste raw email text. Creates the mission and opens it at Intake.
- Supplier persona sees a supplier board: orders published to them, their placements and invoices only.
- Loading skeletons, empty states with a clear call to action, error toasts using ProblemDetail messages.

**Verify**: `cd frontend && npm run lint && npm run typecheck && npm run test && npm run build`

**Done when**: board reflects backend state; creating a mission adds a row without a page reload.

**Commit**: `feat(frontend): missions board with filters, new mission dialog and supplier view`

---

## Slice 13 — Mission workflow screens

**Tasks**
- `/app/missions/[id]`: left phase rail (done / active / locked with padlock; clicking a locked phase shows a toast naming the phase to finalize first), "Other missions" switcher, center workspace, right panel with Artifacts and Audit trail.
- One component per phase reproducing the prototype: Intake (email, order draft with confidence and review), Sourcing (ranked candidate cards with ✓/✗ explanations, excluded candidates with the reason, including "already placed on ORD-xxxx"), Contracts (per-contract checks, fix, sign), Timesheets (hours table with flagged cell, anomaly callout with AI explanation, resolve, approve), Invoice (three-way match table, drafted message, credit note, approve), Closed (summary).
- Gate panel at the bottom: checklist from the backend, Finalize button disabled until all checks pass; finalized phases become read-only.
- Mark every AI-generated element with a small "AI" badge.

**Verify**: `cd frontend && npm run lint && npm run typecheck && npm run test && npm run build`

**Done when**: a Playwright smoke test walks one mission from Intake to Closed against the backend in mock profile.

**Commit**: `feat(frontend): phase-gated mission workflow with artifacts and audit trail`

---

## Slice 14 — Copilot panel and admin monitoring

**Tasks**
- Copilot side sheet available in `/app`: chat, citations as numbered chips that open the source passage, collapsible "steps" trace of tool calls.
- `/app/admin` (admin persona): AI latency p50/p95, tokens and estimated cost per tenant and per feature, error rate, share of AI extractions corrected by humans, latest eval run scores over time (Recharts). Backend endpoints under `/admin/metrics/*`.
- Audit log explorer with filters (mission, actor, AI vs human).

**Verify**: `cd frontend && npm run lint && npm run typecheck && npm run test && npm run build` and `cd backend && ./mvnw verify`

**Done when**: dashboard shows real numbers from `ai_call` after running a mission.

**Commit**: `feat(frontend): copilot with citations and tool trace, admin AI monitoring dashboard`

---

## Slice 15 — Landing page (Webflow-style)

**Goal**: a polished marketing page at `/` that sells the product and links to the live demo. It should feel like a premium Webflow site: generous whitespace, large confident typography, smooth scroll-driven motion, a strong art direction. Avoid generic SaaS-template look (no identical card grids with gradient washes, no all-caps eyebrow labels on every section).

**Sections**
1. **Navbar**: logo, anchors (Product, How it works, Security, FAQ), "Sign in", primary CTA "Try the live demo". Becomes solid with a subtle border after scrolling.
2. **Hero**: headline about one workflow from request to invoice (for example "From the first email to the last invoice, one workflow"), short subline, two CTAs. Right side: a layered, parallax composition of real UI fragments (mission board row, gate checklist, candidate card, three-way match row) built as React components, each layer moving at a different speed on scroll and slightly with the pointer.
3. **Problem strip**: "ten inboxes" visual — scattered email/PDF chips that converge into a single mission row as the user scrolls (scroll-linked transform).
4. **How it works**: sticky scroll section. The left column stays pinned and shows the 6-phase rail; as the user scrolls, each phase activates and the right column swaps the matching screenshot-component with a crossfade.
5. **AI that stays accountable**: three principles with small live UI examples: "The AI drafts, rules decide", "Every suggestion is explained", "A person approves". 
6. **Security and compliance**: tenant and supplier isolation, PII masking before any model call, audit trail, GDPR and EU AI Act readiness (recruitment AI is high-risk: human oversight, transparency, logging). Present as a calm, text-led section with one diagram.
7. **Metrics band**: animated count-up numbers when in view (label them as demo figures).
8. **Testimonials**: carousel or marquee of 4–6 quotes from fictional personas (HR manager, site manager, agency account manager, finance controller). Add a small visible note "Illustrative testimonials" so nothing misleads a reader.
9. **FAQ**: accessible accordion (shadcn Accordion) with 6–8 questions: what data the AI sees, whether the AI can reject a candidate (no), how double-booking is prevented, how tenants are isolated, which models are used and can they be swapped, how quality is measured (evals in CI), GDPR, how to try the demo.
10. **Final CTA**: large statement, "Try the live demo" button, note that the demo resets daily and uses synthetic data.
11. **Footer**: product links, GitHub link, "Demo project, not affiliated with Pixid".

**Motion and technical requirements**
- Framer Motion `useScroll` / `useTransform` for parallax and scroll-linked effects; `whileInView` reveals used sparingly (one orchestrated reveal per section at most). Optional smooth scrolling with Lenis.
- All motion disabled or reduced under `prefers-reduced-motion`.
- Lighthouse targets on the landing page: Performance ≥ 90, Accessibility ≥ 95, SEO ≥ 95. Images via `next/image`, no layout shift from motion, metadata and Open Graph image.
- Fully responsive: parallax layers simplify to a stacked static composition below 768px.

**Verify**: `cd frontend && npm run lint && npm run typecheck && npm run build` and a Playwright test that loads `/`, opens an FAQ item, and clicks "Try the live demo" through to `/login`.

**Done when**: page is complete in light and dark themes, at 360px, 768px and 1440px widths.

**Commit**: `feat(landing): Webflow-style landing page with parallax, sticky how-it-works, testimonials and FAQ`

---

## Slice 16 — Evaluation framework

**Tasks**
- `/evals/datasets`: `intake.jsonl` (≥ 20 emails with expected fields), `invoice.jsonl` (≥ 15 invoices with expected lines), `copilot_rag.jsonl` (≥ 20 questions with expected source document and key facts), `copilot_tools.jsonl` (≥ 15 questions with expected tool and answer value).
- Python runner `python -m evals run --suite all --base-url ...`: calls the backend, computes field-level accuracy, retrieval hit rate, citation validity, tool-choice accuracy, numeric answer accuracy; optional LLM-as-judge faithfulness when a key is present.
- Thresholds in `evals/thresholds.yaml`; exit code non-zero when any metric is below its threshold. Writes `evals/report.json` and posts results to `POST /admin/evals/runs` (with git SHA) for the dashboard.
- In `mock` profile the suite must pass deterministically.

**Verify**: `make eval` (starts backend in mock profile, runs the suite)

**Done when**: report is produced and visible in the admin dashboard; lowering a threshold artificially makes the command fail.

**Commit**: `feat(evals): golden datasets, eval runner with thresholds and dashboard reporting`

---

## Slice 17 — CI/CD and deployment

**Tasks**
- `backend/Dockerfile`: multi-stage build, Temurin 21 JRE, JVM flags for a 512 MB container (`-XX:MaxRAMPercentage=70`, serial GC), port from `$PORT`.
- `render.yaml` blueprint for the backend (free plan, health check `/actuator/health`, env vars listed without values).
- GitHub Actions:
  - `ci.yml` on pull requests and pushes: backend `./mvnw verify` (Testcontainers), frontend lint/typecheck/test/build, eval suite in mock profile, Playwright smoke.
  - `live-evals.yml` (manual dispatch only, to protect OpenAI credits): runs the eval suite against the deployed backend in live profile using the `OPENAI_API_KEY` repository secret.
  - `deploy.yml` on push to `main`, only after CI passes: trigger the Render deploy hook; Vercel deploys the frontend through its Git integration (document the setup).
- `docs/deployment.md`: step-by-step for Neon (enable `vector` and `btree_gist`), Render, Vercel, the OpenAI API key (with a monthly usage limit set in the OpenAI dashboard as a second safety net), and a note about the free-tier cold start (open the app a minute before a demo).

**Verify**: `act` is optional; at minimum validate workflow YAML with `actionlint` if available, and run every CI command locally.

**Done when**: all CI commands pass locally; deployment docs are complete.

**Commit**: `ci: GitHub Actions pipelines with eval gate, Docker image and Render/Vercel deployment`

---

## Slice 18 — Documentation and demo polish

**Tasks**
- README: pitch, live demo link placeholder, screenshots placeholders, architecture diagram (Mermaid), feature list mapped to phases, how to run locally in 3 commands, how to run evals, project structure.
- `docs/architecture.md`: components, request flow, tenant and supplier scoping, AI gateway, data model diagram (Mermaid ER).
- ADRs in `docs/adr/`: 0001 deterministic rules for decisions vs LLM, 0002 tenant scoping in queries and DB constraints, 0003 mock/live AI profiles, 0004 pgvector instead of a dedicated vector DB, 0005 evals as a CI gate.
- `docs/demo-script.md`: a 3-minute walkthrough (board, new mission, low-confidence field, double-booking exclusion, contract fix, timesheet anomaly, invoice mismatch, copilot with citations, supplier trying to read a competitor's rates, admin dashboard, CI eval gate) and a "limits and what I'd do at scale" section.
- Final pass: remove dead code, make sure `make dev` starts everything, update `PROGRESS.md` with a final summary.

**Verify**: `make test && make eval && cd frontend && npm run build`

**Done when**: a new developer can clone and run the demo locally by following the README only.

**Commit**: `docs: README, architecture, ADRs and demo script`
