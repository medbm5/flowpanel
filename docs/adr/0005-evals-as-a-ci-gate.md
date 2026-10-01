# ADR 0005 — Evals as a CI gate

- Status: accepted
- Date: 2026-10-01

## Context

Prompt, model or parsing changes can silently degrade extraction accuracy, retrieval or tool use. Unit tests cover the deterministic
rules but not the quality of AI outputs.

## Decision

- Golden datasets (synthetic) live in `evals/datasets`: intake emails, invoices, RAG questions (including unanswerable ones and
  cross-tenant twins) and tool questions (including supplier-scope cases).
- `python -m evals run` calls the real backend API and computes field accuracy, invoice line/total accuracy, retrieval hit rate,
  citation validity, answer accuracy, tool-choice and answer accuracy (and optional LLM-judge faithfulness).
- Thresholds in `evals/thresholds.yaml`; any metric below its threshold exits non-zero, which fails the `CI` workflow.
- Every run writes `evals/report.json` and posts its scores with the git SHA to the admin dashboard.
- CI runs the suite against the mock profile (free, deterministic). Live models are scored with a manual workflow.

## Consequences

- Regressions in the deterministic stand-ins and in the API contract are caught on every pull request.
- The live run gives a comparable number when changing models or prompts; thresholds may differ per profile in the future.
- Datasets must grow with features; a new AI feature is not "done" without eval cases.
