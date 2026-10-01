# Architecture

Flowpanel is a mini vendor management system (VMS) for temporary staffing. A **mission** moves through six phases —
Intake → Sourcing → Contracts → Timesheets → Invoice → Closed — and each phase opens only when the previous phase's **gate** passes and
a person finalizes it. An AI layer helps at every phase, but never decides.

## Components

```mermaid
flowchart LR
  subgraph Browser
    L[Landing page /]
    A[Product app /app]
  end
  subgraph Vercel["Next.js (Vercel)"]
    RW["/api/* rewrite"]
    PX["proxy.ts — session cookie check"]
  end
  subgraph Render["Spring Boot (Render, Docker)"]
    SEC[JWT cookie filter → RequestContext]
    MS[Mission + phase services]
    GATES[Gate validators]
    RULES["Rules engines (contracts, timesheets, 3-way match, ranking)"]
    GW[AiGateway]
    AUD[AuditService]
  end
  subgraph Neon["PostgreSQL 16 + pgvector"]
    DB[(tables, EXCLUDE constraint, HNSW index)]
  end
  OAI[(OpenAI — live profile only)]
  MOCK[[Mock model client — default]]

  A --> RW --> SEC --> MS
  L -.-> A
  A --> PX
  MS --> GATES
  MS --> RULES
  MS --> GW
  MS --> AUD
  GW -->|masked prompt| OAI
  GW --> MOCK
  GW --> AUD
  MS --> DB
  AUD --> DB
  GW -->|ai_call rows| DB
```

| Layer | Technology | Notes |
|---|---|---|
| Frontend | Next.js 16 (App Router), TypeScript strict, Tailwind 4, shadcn/ui, TanStack Query, Framer Motion, Recharts | Calls only `/api/*` (same origin), types generated from OpenAPI |
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA + JdbcTemplate, Flyway, springdoc | Package by feature |
| AI | Spring AI 1.1 (OpenAI chat with JSON-schema outputs, tools, embeddings) | Behind `AiGateway`; `mock` profile by default |
| Data | PostgreSQL 16, pgvector (HNSW, cosine), btree_gist (exclusion constraint) | Neon in production, Docker locally |
| Quality | JUnit 5, AssertJ, Testcontainers, Vitest, Testing Library, Playwright, Python eval suite | Evals are a CI gate |

Backend packages: `tenant`, `auth`, `mission` (state machine, gates, artifacts), `intake`, `sourcing`, `contract`, `timesheet`,
`invoice` (+ closing summary), `copilot` (RAG + tools), `ai` (gateway, masking, metrics, mock and live clients), `audit`, `metrics`
(admin dashboard), `demo` (seeder and reset).

## Request flow

1. The browser calls `/api/missions/42/contracts/sign` on the Vercel origin; Next rewrites it to `${BACKEND_URL}/missions/42/contracts/sign`.
   The `fp_session` cookie (httpOnly, SameSite=Lax, signed HS256 JWT) travels with it.
2. `JwtCookieAuthFilter` validates the token and puts a `CurrentUser` (user, role, tenantId, supplierId) in the security context.
3. The controller calls the feature service. Every phase-specific write starts with
   `MissionService.requireCurrentPhase(id, Phase.CONTRACTS)`: it loads the mission **of the caller's tenant** with a row lock and returns
   409 if the mission is in another phase (404 if it belongs to another tenant).
4. The service applies deterministic rules, calls the AI gateway when it needs a draft or an extraction, writes artifacts and audit events.
5. Finalizing a phase: `GateRegistry` evaluates the phase's `GateValidator`; unmet checks → 409 with `unmetChecks`. Otherwise a
   `phase_completion` row is written, `PhaseFinalized` / `PhaseEntered` events let feature modules close and open their phase
   (e.g. timesheets are generated when TIMESHEETS opens, the summary when CLOSED opens), all in the same transaction.
6. Errors are RFC 7807 `ProblemDetail`; the UI shows `detail` (and `unmetChecks`).

## Phase state machine

```mermaid
stateDiagram-v2
  [*] --> INTAKE
  INTAKE --> SOURCING: order extracted, no field to review
  SOURCING --> CONTRACTS: published, positions filled
  CONTRACTS --> TIMESHEETS: contracts generated, no blocking issue, all signed
  TIMESHEETS --> INVOICE: checks run, anomalies resolved, approved
  INVOICE --> CLOSED: invoices received, three-way match passes, approved
  CLOSED --> [*]
```

`Phase` holds an explicit allowed-transitions map; `Mission.transitionTo` rejects anything else. The board's "next action" and
"Needs review" tag come from the same gate evaluation (a failing check flagged `needsReview` means a person must look at something).

## Tenant and supplier scoping

- **Buyers** (`BUYER`) belong to a tenant. Every repository query on missions and their data filters by `tenant_id` from the session.
- **Supplier users** (`SUPPLIER`) belong to a supplier. They reach only orders published to their supplier (`mission_supplier`), and
  inside those only their own candidates, placements, invoices and contracts (`supplier_id` filter in SQL).
- **Admin** sees platform metrics and the audit explorer, not mission screens.
- Out-of-scope ids answer **404, not 403**, so a caller cannot probe which ids exist (`IsolationIT`, `MissionIT`).
- The copilot's tools read the scope from the session, so arguments chosen by the model can narrow a query but never widen it
  (`CopilotIT`: a supplier asking for a competitor's rates with a prompt injection gets only its own row).
- Document retrieval filters `tenant_id` **in the same SQL statement** as the vector ordering, with
  `hnsw.iterative_scan = relaxed_order` so the filter cannot starve the HNSW index.
- Double booking is impossible at the database level: `placement` has
  `EXCLUDE USING gist (worker_id WITH =, period WITH &&)`; concurrent selections yield exactly one success (`SourcingIT`).

## AI gateway

```mermaid
sequenceDiagram
  participant S as Feature service
  participant G as DefaultAiGateway
  participant M as PiiMasker
  participant C as AiModelClient (mock | OpenAI)
  participant R as AiCallRecorder
  S->>G: structured(prompt, OrderExtraction.class)
  G->>G: budget + rate limit (live profile)
  G->>M: mask names, emails, phones → [PERSON_1]
  G->>C: masked prompt + JSON schema + max_tokens
  C-->>G: JSON
  G->>M: unmask
  G->>G: parse + Bean Validation + semantic checks
  alt invalid
    G->>R: ai_call INVALID_OUTPUT + audit
    G->>C: retry once with the validation error
  end
  G->>R: ai_call OK (tokens, latency, cost, prompt hash) + audit (own transaction)
  G-->>S: AiResult<T>
```

- One implementation (`DefaultAiGateway`) for both profiles; only the provider client changes, so masking, retries, spend protection and
  metrics are exercised by every test.
- `mock` (default): deterministic responders per prompt template id (a rule-based French email parser, an invoice-text parser, a keyword
  tool router, template drafts), hash-based 1536-d embeddings, simulated tokens and latency.
- `live`: Spring AI `OpenAiChatModel` with JSON-schema structured outputs (`BeanOutputConverter`), function calling (`ToolCallback`),
  `OpenAiEmbeddingModel`. Model names come from configuration.
- Spend protection: `AI_DAILY_BUDGET_USD` from `ai_call` cost records, per-tenant rate limit, `max_tokens` on every call, typed errors
  (`ai_budget_exceeded`, `ai_rate_limited`, `ai_invalid_output`, `ai_provider_error`).
- Embeddings are cached by `(content hash, model)` in `embedding_cache`.

## Data model

```mermaid
erDiagram
  TENANT ||--o{ TENANT_SUPPLIER : panel
  SUPPLIER ||--o{ TENANT_SUPPLIER : panel
  TENANT ||--o{ APP_USER : buyers
  SUPPLIER ||--o{ APP_USER : "supplier users"
  SUPPLIER ||--o{ WORKER : pool
  TENANT ||--o{ MISSION : owns
  MISSION ||--o{ PHASE_COMPLETION : finalized
  MISSION ||--o{ ARTIFACT : produces
  MISSION ||--o| INTAKE_DRAFT : order
  MISSION ||--o{ MISSION_SUPPLIER : "published to"
  MISSION ||--o{ CANDIDATE : proposals
  WORKER ||--o{ CANDIDATE : proposed
  CANDIDATE ||--o| PLACEMENT : selected
  PLACEMENT ||--|| CONTRACT : contracted
  CONTRACT ||--o{ TIMESHEET : weeks
  TIMESHEET ||--o{ TIMESHEET_ANOMALY : flags
  MISSION ||--o{ INVOICE : "per supplier"
  TENANT ||--o{ DOCUMENT : policies
  DOCUMENT ||--o{ DOCUMENT_CHUNK : "chunks + vector(1536)"
  MISSION ||--o{ AUDIT_EVENT : trail
  MISSION ||--o{ AI_CALL : metrics
  PLACEMENT {
    bigint worker_id
    daterange period "EXCLUDE (worker_id =, period &&)"
  }
  DOCUMENT_CHUNK {
    bigint tenant_id
    vector embedding "HNSW cosine"
  }
```

Migrations: `V1` extensions · `V2` tenancy, users, audit · `V3` workers, templates, missions, artifacts · `V4` ai_call, embedding cache,
known contacts · `V5` intake · `V6` sourcing · `V7` contracts · `V8` timesheets · `V9` invoices · `V10` documents · `V11` eval runs.
