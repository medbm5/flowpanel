# Progress log

Run log for the BUILD_PLAN.md slices. One entry per slice.

## Blocked / follow-ups

- **Landing page Lighthouse, mobile preset**: Accessibility 100, SEO 100, Best Practices 100; Performance **90 on the desktop preset** but
  62–76 on the simulated mobile preset when measured on the (busy) local Windows dev machine (TBT dominated by hydration of the motion
  sections). Follow-up: measure on the Vercel deployment, and if still under 90, defer the below-the-fold motion sections with
  `next/dynamic` and drop the `backdrop-blur` on the hero fragments.

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

### Slice 4 — AI gateway, PII masking and AI call metrics
- Built: `AiGateway` (`structured`, `text`, `embed`, `withTools`) implemented once by `DefaultAiGateway`; the AI profile only swaps the
  provider SPI `AiModelClient`: `MockAiModelClient` (default; deterministic responders keyed by prompt template id, hash-based
  1536-d embeddings, simulated tokens and latency) or `SpringAiModelClient` (live; Spring AI 1.1.8 `OpenAiChatModel` with JSON-schema
  structured outputs via `BeanOutputConverter`, function calling via `ToolCallback`, `OpenAiEmbeddingModel`). `PiiMasker`
  (emails, phones, known names → `[PERSON_n]`/`[EMAIL_n]`/`[PHONE_n]`, restored after the call; tool args unmasked / tool results masked),
  `PiiDirectory` (workers, users, `known_contact`), `ai_call` + `AiCallRecorder` (own transaction, audit event per attempt),
  price table in config (gpt-4o-mini $0.15/$0.60, text-embedding-3-small $0.02 per 1M), `SpendGuard` (daily budget from live `ai_call`
  cost, per-tenant 1-minute rate limit), `max_tokens` on every call, one retry on invalid structured output with the error appended,
  typed `AiException` codes (`ai_budget_exceeded`, `ai_rate_limited`, `ai_invalid_output`, `ai_provider_error`), `EmbeddingService`
  with a content-hash `embedding_cache`.
- Tests: `PiiMaskerTest` (round trips), `DefaultAiGatewayTest` (retry, double failure, masking, budget, rate limit, tools, provider error),
  `SpendGuardTest`, `AiGatewayIT` (ai_call row + audit event per call, provider never receives PII, cache hit avoids re-embedding).
  `SpringAiModelClientLiveTest` is opt-in (`OPENAI_LIVE_SMOKE=true`) and was run once against OpenAI: structured output, tool calling and
  embeddings all pass.
- Deviations: instead of separate `SpringAiGateway`/`MockAiGateway` classes, one gateway + two provider clients, so masking, spend
  protection, retries and metrics are tested identically in both profiles. Mock calls are recorded as `mock/<model>` and priced like the
  model they simulate (dashboard shows realistic numbers); the budget only counts `profile = live` rows. Spring AI is used without its
  auto-configuration so the mock profile never needs a key.

### Slice 5 — Intake phase
- Built: `POST /missions/{id}/intake/extract` (structured output `OrderExtraction`: 12 fields, each with value + confidence),
  `POST /missions/{id}/intake/fields/{field}/confirm` (optional corrected value, re-validated), `GET /missions/{id}/intake`,
  `POST /intake/preview` (stateless, for evals). `OrderValidator` (dates coherent, quantity > 0, rate > 0, reason in
  ACTIVITY_INCREASE / REPLACEMENT / SEASONAL, replaced employee required for REPLACEMENT, ≤ 18 months). Fields failing validation or under
  the confidence threshold (0.75, config) are `needsReview`. Real `IntakeGateValidator` (extracted + nothing to review; drives "Needs review"
  and next action). ORDER artifact DRAFT → CONFIRMED on finalize. Raw-email missions are renamed from the extraction.
- Mock: `MockIntakeResponder` runs a rule-based French email extractor (`HeuristicOrderExtractor`) on the masked email; hedged values
  ("normalement", "autour de", "dès que possible") get low confidence, so each template has exactly one flagged field
  (forklift → end date, pickers → hourly rate, office → start date). Markers `#mock-invalid-once` / `#mock-invalid-always` exercise retries.
- Tests: `HeuristicOrderExtractorTest`, `OrderValidatorTest`, `IntakeIT` (valid extraction, invalid output + retry, double failure → 502
  `ai_invalid_output`, low-confidence flow, correction, gate fail and pass, PII masking of the replaced employee, phase/tenant guards).
- Deviations: added `weeklyHours`, `requiredCertifications` and `overtimeAllowed` to `OrderDraft` because sourcing, timesheets and
  the rules engines need them. The seeder now runs the real extraction for seeded missions.

### Slice 6 — Sourcing phase with matching and double-booking protection
- Built: `mission_supplier`, `candidate`, `placement` (`daterange` + `EXCLUDE USING gist (worker_id WITH =, period WITH &&)`).
  `POST /missions/{id}/sourcing/publish` (order sent to every panel supplier; each supplier proposes its 4 closest profiles),
  `POST|DELETE /missions/{id}/sourcing/select/{candidateId}` (capped at the order quantity, mission row lock, 409 "already placed on
  ORD-xxxx" from a pre-check or, under concurrency, from the constraint violation + a lookup in a fresh transaction),
  `GET /missions/{id}/sourcing`. `CandidateRanking` (pure Java): stage 1 hard rules (missing certification, partial availability,
  overlapping placement), stage 2 score = 0.5 × embedding similarity + 0.3 × distance (gazetteer + haversine) + 0.2 × experience;
  ✓/✗ explanations built from the same inputs. One-sentence AI summary (`sourcing.summary`, facts only) for the top 3.
  Real `SourcingGateValidator` (published + positions filled), SHORTLIST artifact (DRAFT → FINAL), `MissionPositions` implementation for
  the board. Supplier portal: `GET /supplier/orders`, `GET /supplier/orders/{id}` (only orders published to the caller's supplier, only
  their own proposals and placements; 404 otherwise).
- Seed: ORD-0142 places Karim Haddad and Lucas Petit (so new Lesquin forklift orders show "Already placed on ORD-2026-0142");
  ORD-0147 is published with ranked proposals.
- Tests: `CandidateRankingTest` (each rule pass/fail, scoring monotonicity), `SourcingIT` (reasons, AI summaries, cap, undo, excluded
  candidate, cross-mission double booking, **concurrent selections → exactly one 200 and one 409**, supplier scoping, phase guard).

### Slice 7 — Contracts phase
- Built: `contract` table; `POST /missions/{id}/contracts/generate` (one contract per placement, ref `CT-<mission number>-0N`,
  structured terms copied deterministically from the order and placement, body drafted by the LLM `contract.draft` from masked facts),
  `GET /missions/{id}/contracts`, `POST /contracts/{id}/fix/{ruleId}`, `POST /missions/{id}/contracts/sign` (simulated e-signature by
  client and supplier, refused while a blocking issue remains). `ContractRulesEngine` (pure Java): RATE_MATCHES_ORDER,
  LEGAL_REASON_PRESENT, CERTIFICATE_ATTACHED (fixable only if the worker holds it), DATES_WITHIN_ORDER; auto-fixes derive from the order.
  The first contract is generated with the end date one week after the order end (seeded blocking issue). Real `ContractsGateValidator`
  (generated, no blocking issue → "Needs review", all signed); CONTRACT artifacts DRAFT → SIGNED.
- Tests: `ContractRulesEngineTest` (each rule pass and fail, fixes), `ContractsIT` (full generate → blocked sign → fix → sign → finalize,
  AI draft restored with the worker name that the provider never saw, double generation, cross-tenant fix → 404).

### Slice 8 — Timesheets phase
- Built: `timesheet` (daily hours Mon..Sun as jsonb), `timesheet_anomaly`, `timesheet_check_run`. Entering TIMESHEETS generates one
  supplier-submitted sheet per contract and week (pro-rated partial weeks); the first full week of the first contract is submitted with
  41 h vs 35 h contracted (seeded anomaly). `TimesheetRulesEngine` (pure Java, BigDecimal): CONTRACTED_HOURS (over contract without
  agreed overtime, flags the days above the daily schedule), WEEKLY_MAXIMUM (48 h), DAILY_LIMIT (10 h); approved-hours computation.
  `POST /missions/{id}/timesheets/check` (anomalies + 2-sentence AI explanation `timesheet.explain` from the daily breakdown),
  `POST /anomalies/{id}/resolve` (`APPROVE_OVERTIME` or `RETURN_TO_SUPPLIER` → simulated corrected sheet), `POST
  /missions/{id}/timesheets/approve` (approved hours computed in Java), `GET /missions/{id}/timesheets`. Real gate (checked,
  anomalies resolved → "Needs review", approved); TIMESHEETS artifact SUBMITTED → APPROVED.
- Tests: `TimesheetRulesEngineTest` (each rule, pro-rating, approved hours for both resolution paths), `TimesheetsIT` (seeded mission,
  approve-overtime path = 146 h, return-to-supplier path = 140 h, double resolution 409, tenant scoping). Shared `MissionFlow` test helper
  drives fresh missions through the real endpoints.

### Slice 9 — Invoice phase and three-way match
- Built: `invoice` table; PDFBox 3.0.8 `InvoicePdf` (renders a synthetic supplier invoice per supplier — the mission's first invoice bills
  3 h more than approved — and extracts the text back). `POST /missions/{id}/invoice/receive` (PDF → text → LLM structured `InvoiceLines`,
  validated: amount = hours × rate, total = sum; retried once if not), `ThreeWayMatcher` (pure Java, BigDecimal, HALF_UP to the cent:
  MATCH / HOURS_MISMATCH / RATE_MISMATCH / AMOUNT_MISMATCH / UNKNOWN_WORKER / MISSING_LINE), French credit-note request drafted by the LLM
  (`invoice.message`, masked names), `POST .../invoice/request-credit-note` (simulated credit note, re-match passes, CREDIT_NOTE artifact),
  `POST .../invoice/approve`, `GET /invoices/{id}/pdf`, `POST /invoices/extract` (evals), `GET /supplier/invoices` (own supplier only).
  Finalizing INVOICE closes the mission; `ClosingService` computes the summary (workers placed, hours approved, amount approved,
  overbilling avoided, AI steps reviewed, human decisions, AI fields corrected) and a SUMMARY artifact; `GET /missions/{id}/summary`.
- Tests: `ThreeWayMatcherTest` (exact match, hour mismatch, rate mismatch, rounding, unknown/missing), `InvoicePdfTest` (PDF round trip +
  extraction + validation), `InvoiceIT` (Intake → Closed end to end with the credit note flow and summary figures, PDF tenant scoping,
  supplier invoice list, text extraction endpoint).

### Slice 10 — Copilot: tool calling and document Q&A with citations (RAG)
- Built: `document`, `document_chunk` (`tenant_id` denormalized, `vector(1536)`, HNSW `vector_cosine_ops` index). 4 synthetic policy documents
  for LogiNord and 3 for MétalPro (night work, safety, panel agreement, timesheets), with near-identical night-work policies (25 % vs 40 %
  bonus) to prove isolation. `Chunker` (headings first, ~500 tokens, ~60 overlap), `DocumentService` (ingest: extract → chunk → embed with
  content-hash cache; `POST /documents` PDF or Markdown upload; `GET /documents`; search with `WHERE tenant_id = ?` in the same SQL as the
  vector ordering, `hnsw.iterative_scan = relaxed_order` so the filter never starves the index). `CopilotTools`: `listMissions`,
  `getMissionStatus`, `getSpendBySupplier`, `listOpenAnomalies`, `listContractsEndingBefore`, `searchDocuments`, all reading the scope from
  the session (supplier users only see their supplier). `POST /copilot/ask` returns answer, citations, retrieved sources and the tool-call
  trace; citations not pointing at a passage retrieved in this request are stripped and an uncited document answer becomes
  "Not found in your documents.". Mock copilot routes by keywords and answers only from tool results (quotes + cites the best sentence).
- Tests: `ChunkerTest` (+ citation verification), `CopilotIT` (tenant-isolated retrieval with near-identical documents, verbatim
  cross-tenant query, citation validity, not-found, every data tool, out-of-scope mission, **supplier asking for another supplier's rates
  with a prompt injection gets only its own data**, upload + search + duplicate upload).

### Slice 11 — Frontend foundation
- Built: Next.js 16.3 (App Router, TypeScript strict, ESLint, Tailwind CSS 4) with shadcn/ui (radix base) components (Button, Badge, Card,
  Dialog, Tabs, Toast via Sonner, Accordion, Sheet, Select, Tooltip, Skeleton...). "Schibsted Grotesk" via `next/font` with a system
  fallback. Design tokens as CSS variables (cobalt #2F4BD6, ok #1F7A55, warn #9A6514, bad #B4362F, slate neutrals) for light and dark
  themes (next-themes), mapped onto the shadcn variables; reduced-motion and visible focus rules. Rewrite `/api/:path*` → `${BACKEND_URL}/:path*`.
  Typed API client: `openapi-typescript` → `src/lib/api/schema.d.ts` (`npm run gen:api`) + `openapi-fetch`, TanStack Query for data.
  Routes `/` (placeholder), `/login` (persona picker), `/app` (protected), app shell (organization, user, theme toggle, logout).
  Playwright smoke `e2e/login.spec.ts` (redirect to login, persona login lands on `/app`) passes against the local backend.
- Deviations: Next.js 16 renamed Middleware to **Proxy**, so the auth-cookie check lives in `src/proxy.ts`. Backend: an OpenAPI customizer marks
  record properties as required so generated types are strict. `@vitejs/plugin-react` is not used (peer conflict with Babel 8 in its
  optional deps); Vitest 5 transforms TSX natively. The generated `schema.d.ts` is committed so builds don't need a running backend.

### Slice 12 — Missions board
- Built: `/app` board for buyers — one row per mission (ref, title, site, positions filled, 6-segment `PhaseProgress`, next action,
  "Needs review" tag), filters All / In progress / Needs review / Closed (server-side `status` param), "New mission" dialog (template
  cards or raw email textarea) that inserts the created mission into the cached board (no reload) and opens it at Intake. Supplier persona
  gets a supplier board: orders published to them (expandable: own proposals and placements) and their invoices. Admin is redirected to
  `/app/admin`. Loading skeletons, empty states with a call to action, ProblemDetail messages in error boxes/toasts.
- Tests: Vitest + Testing Library (`PhaseProgress`, `MissionBoard`: rows, review tag, empty state, error message, creation adds a row without
  reload); Playwright `e2e/board.spec.ts` against the running backend (board state, new mission, supplier board).

### Slice 13 — Mission workflow screens
- Built: `/app/missions/[id]` — left phase rail (done ✓ / active / locked with padlock; clicking a locked phase toasts "Finalize <phase>
  first"), "Other missions" switcher, center workspace, right panel with Artifacts (invoice PDFs linked) and the Audit trail (filter All /
  AI / People). One component per phase: Intake (email + order draft with confidence bars, flagged fields, confirm / edit), Sourcing
  (ranked candidate cards with ✓/✗ explanations and AI summary, excluded candidates with reasons incl. "Already placed on ORD-…"),
  Contracts (per-contract rule checks, one-click fixes, AI draft text, e-signature), Timesheets (hours grid with flagged cells, anomaly
  callouts with AI explanation, approve overtime / return to supplier, approve), Invoice (three-way match table, AI-drafted French message,
  credit note, approve, PDF), Closed (summary). Gate panel under the active phase: backend checklist, Finalize disabled until ready;
  finalized phases are read-only. Every AI-generated element carries the "AI" badge.
- Tests: Vitest (`GatePanel` disabled/enabled, `PhaseRail` locked toast) and **Playwright `e2e/mission-flow.spec.ts` walking one mission from
  Intake to Closed against the backend in mock profile** (resets the demo through the admin endpoint first, so it is repeatable).

### Slice 14 — Copilot panel and admin monitoring
- Built: copilot side sheet in `/app` (buyers and suppliers): chat, suggestions, answers with `[n]` citations rendered as numbered chips that
  open the source passage, collapsible "steps" trace of tool calls (name, arguments, result). `/app/admin` (admin persona): stat tiles (calls,
  p50/p95 latency, error rate, tokens, estimated cost, live spend today vs budget, share of AI extractions corrected by humans), Recharts bars
  (latency p50/p95 per feature, estimated cost per tenant), per-feature table (table view of the charts), eval scores over time (line chart,
  empty state until Slice 16), audit log explorer (mission, actor, AI / human / system), demo reset button.
  Backend: `GET /admin/metrics/overview`, `GET /admin/metrics/evals`, `POST /admin/evals/runs` (`eval_run` table), `GET /admin/audit`.
- Chart palette (categorical order) validated with the dataviz validator in light (#ffffff) and dark (#111830) modes.
- Tests: `AdminMetricsIT` (real numbers after running a mission, eval run round trip, audit filter, admin-only), Playwright
  `e2e/copilot-admin.spec.ts` (cited answer + passage dialog + steps; dashboard shows non-zero ai_call numbers).

### Slice 15 — Landing page (Webflow-style)
- Built: `/` with navbar (anchors, Sign in, CTA; turns solid with a border on scroll; mobile menu), hero (large headline, two CTAs, layered
  parallax composition of real UI fragments — mission row, gate checklist, candidate card, three-way match row — each layer at its own scroll
  speed and following the pointer; stacked and static below 768px), "ten inboxes" problem strip (scattered email/PDF chips converge into a
  mission row, scroll-linked), sticky How-it-works (pinned 6-phase rail, crossfading visual per phase; stacked list below 1024px),
  "AI that stays accountable" (three principles with live fragments), Security & compliance (text-led + one SVG data-flow diagram),
  metrics band (count-up when in view, labelled as demo figures), testimonials marquee (pauses on hover/focus, "Illustrative testimonials"),
  FAQ (shadcn Accordion, 8 questions), final CTA (demo resets daily, synthetic data), footer ("Demo project, not affiliated with Pixid").
  Framer Motion `useScroll` / `useTransform` / `whileInView` (one reveal per section), loaded through `LazyMotion`; all motion off under
  `prefers-reduced-motion`. Metadata, Open Graph image (`opengraph-image.tsx`), `robots.txt`, `sitemap.xml`.
- Tests: Playwright `e2e/landing.spec.ts` (loads `/`, opens an FAQ item, "Try the live demo" → `/login`; no horizontal overflow at 360 / 768 /
  1440 px in light and dark). Screenshots reviewed at each width.
- Deviations: Lenis smooth scrolling not added (CSS `scroll-behavior: smooth` instead, to keep JS small). No raster images are used, so
  `next/image` isn't needed. Query/toast providers moved from the root layout into `/app` and `/login` layouts so the landing page doesn't
  load them. `framer-motion` is pinned to 13.4.6 with `motion-dom` 13.4.5 via npm overrides: 13.5.0 was published mid-run with a missing
  export / unpublished tarball.

### Slice 16 — Evaluation framework
- Built: `evals/datasets` — `intake.jsonl` (21 French emails with expected fields), `invoice.jsonl` (16 invoice texts with expected lines),
  `copilot_rag.jsonl` (22 questions with expected source document and key facts, incl. 2 unanswerable and cross-tenant twins),
  `copilot_tools.jsonl` (16 questions with expected tool and answer value, incl. supplier-scope cases). Stdlib-only runner
  `python -m evals run --suite all --base-url ...` (field accuracy, retrieval hit rate, citation validity, answer facts, tool-choice and
  answer accuracy; optional LLM-as-judge faithfulness with `--judge` when `OPENAI_API_KEY` is set). Thresholds in `evals/thresholds.yaml`,
  non-zero exit below threshold, `evals/report.json`, results posted to `POST /admin/evals/runs` with the git SHA; the admin dashboard shows
  scores over time and the latest values vs thresholds. `make eval` → `evals/run_mock.sh` (throwaway database, backend jar in mock profile on
  :8081, suite, shutdown).
- Results (mock profile, deterministic): intake 1.00, invoice lines 1.00 / totals 1.00, RAG hit rate 1.00, citation validity 1.00, answer
  accuracy 0.91, tool choice 1.00, tool answers 1.00 → PASS. Raising `rag.answer_accuracy` to 0.95 makes the command exit 1 (verified).
- Mock improvements found by the evals (not dataset edits): position cleanup drops "obligatoire / exigée" words; the mock copilot no longer
  routes "invoices paid within…" questions to the spend tool, skips section headings and requires the sentence to cover ~40 % of the
  question terms (so unanswerable questions return "not found").
- Deviations: Python 3.11 locally (CI uses 3.12); the eval backend runs with a neutral `ci` Spring profile (the `prod` profile's Secure cookie
  would not be sent over plain HTTP).
