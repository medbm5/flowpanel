package com.flowpanel.audit;

public enum ActorKind {
    /** A person took a decision (confirm, select, approve, finalize...). */
    HUMAN,
    /** The AI produced a suggestion (extraction, ranking summary, draft, explanation...). */
    AI,
    /** Deterministic platform action (seed, generated timesheets...). */
    SYSTEM
}
