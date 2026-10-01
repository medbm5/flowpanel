"use client";

import { CheckCircle2, Circle, Loader2, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { MissionDetail } from "@/lib/api/types";
import { PHASE_LABEL } from "@/lib/format";
import { useFinalize } from "@/lib/mission-hooks";
import { cn } from "@/lib/utils";

/** Deterministic checklist from the backend; Finalize stays disabled until every check passes. */
export function GatePanel({ mission, onFinalized }: { mission: MissionDetail; onFinalized?: () => void }) {
  const finalize = useFinalize(mission.id);
  if (mission.closed) return null;
  const { gate } = mission;
  const label = PHASE_LABEL[gate.phase];
  return (
    <section aria-labelledby="gate-title" className="rounded-xl border bg-card p-4" data-testid="gate-panel">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <ShieldCheck className="size-4 text-cobalt" aria-hidden />
          <h2 id="gate-title" className="text-sm font-semibold">
            {label} gate
          </h2>
          <span className="text-xs text-muted-foreground">Rules decide; you finalize.</span>
        </div>
        <Button
          disabled={!gate.ready || finalize.isPending}
          onClick={() => finalize.mutate(gate.phase, { onSuccess: () => onFinalized?.() })}
          data-testid="finalize"
        >
          {finalize.isPending && <Loader2 className="animate-spin" aria-hidden />}
          Finalize {label.toLowerCase()}
        </Button>
      </div>
      <ul className="mt-3 grid gap-1.5 sm:grid-cols-2">
        {gate.checks.map((c) => (
          <li key={c.id} className="flex items-start gap-2 text-sm" data-passed={c.passed}>
            {c.passed ? (
              <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-ok" aria-label="Passed" />
            ) : (
              <Circle className={cn("mt-0.5 size-4 shrink-0", c.needsReview ? "text-warn" : "text-muted-foreground")} aria-label="Not met" />
            )}
            <span className={cn(!c.passed && "text-muted-foreground", c.needsReview && "text-warn")}>{c.label}</span>
          </li>
        ))}
      </ul>
      {!gate.ready && <p className="mt-3 text-xs text-muted-foreground">Next: {mission.nextAction}</p>}
    </section>
  );
}
