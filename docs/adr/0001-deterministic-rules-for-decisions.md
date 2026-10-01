# ADR 0001 — Deterministic rules make decisions; the LLM extracts, explains and drafts

- Status: accepted
- Date: 2026-10-01

## Context

Temporary staffing decisions have legal and financial consequences: a missing certificate, a contract outside the order period,
overtime that was never agreed, an invoice billing hours that were not approved. Recruitment AI is classified as high-risk under the EU
AI Act, which requires human oversight, transparency and logging. LLMs are good at reading messy emails and PDFs and at writing,
but are non-deterministic and cannot be unit-tested like code.

## Decision

- The LLM **extracts** (email → order draft, invoice PDF → lines), **explains** (candidate summary, anomaly explanation) and **drafts**
  (contract text, credit-note request). Every AI output is shown with an "AI" badge and can be reviewed.
- **Decisions are Java code with unit tests**: order validation, candidate exclusion and scoring (`CandidateRanking`), contract compliance
  (`ContractRulesEngine`), timesheet anomalies and approved hours (`TimesheetRulesEngine`), the three-way match in `BigDecimal`
  (`ThreeWayMatcher`), gates and phase transitions (`GateValidator`, `Phase`).
- Structured outputs are validated server-side; low-confidence or invalid fields require a human confirmation before the gate opens.
- A person finalizes every phase; the audit trail records both the AI suggestion and the human decision.

## Consequences

- Rules are explainable: candidate ✓/✗ lists and contract checks are generated from rule inputs, not by the model.
- Swapping or degrading the model never changes a decision, only the quality of drafts and extractions — which the evals measure.
- Some flexibility is lost (e.g. the model cannot "judge" a borderline candidate); that is intentional.
