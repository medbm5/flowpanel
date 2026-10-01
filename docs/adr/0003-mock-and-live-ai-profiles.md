# ADR 0003 — Mock and live AI profiles behind one gateway

- Status: accepted
- Date: 2026-10-01

## Context

The demo must run without an API key (reviewers, CI, offline), tests must be deterministic, and real model calls cost money.
At the same time the masking, retry, budget and metrics code must be exercised exactly as in production.

## Decision

- `AiGateway` is implemented once (`DefaultAiGateway`); the AI profile swaps only the provider SPI `AiModelClient`:
  - `mock` (default, `AI_PROFILE` unset): deterministic responders keyed by prompt template id — a rule-based French email parser,
    an invoice-text parser, a keyword tool router, template drafts — hash-based 1536-d embeddings (same dimension as the live model,
    so the schema and indexes are identical), simulated tokens and latency. Mock calls are priced like the model they simulate so the
    dashboard shows realistic costs; they never count against the live budget.
  - `live`: Spring AI OpenAI chat (JSON-schema structured outputs, function calling) and embeddings; model names from config.
- Spring AI is used without auto-configuration so the mock profile never needs a key.
- Responders see the **masked** prompt, like a real provider, so PII restoration is tested in mock mode too.

## Consequences

- CI, Playwright and the eval gate run for free and deterministically.
- The mock is a realistic baseline, not a lookup of expected answers: the eval suite caught real weaknesses in it.
- Live quality is measured separately (manual *Live evals* workflow) to protect credits.
- An opt-in live smoke test (`OPENAI_LIVE_SMOKE=true`) checks the real structured-output, tool-calling and embedding paths.
