"use client";

import { BadgeCheck } from "lucide-react";
import type { MissionDetail } from "@/lib/api/types";
import { formatHours, money } from "@/lib/format";
import { PhaseSection } from "../phase-section";

export function ClosedPhase({ mission }: { mission: MissionDetail }) {
  const s = (mission.summary ?? {}) as Record<string, number | string>;
  const stats: { label: string; value: string; hint?: string }[] = [
    { label: "Workers placed", value: String(s.workersPlaced ?? "—") },
    { label: "Hours approved", value: formatHours(s.hoursApproved) },
    { label: "Amount approved", value: money(s.amountApproved) },
    { label: "Overbilling avoided", value: money(s.overbillingAvoided), hint: "credit notes" },
    { label: "AI steps reviewed", value: String(s.aiStepsReviewed ?? "—"), hint: "every one checked by a person" },
    { label: "Human decisions", value: String(s.humanDecisions ?? "—"), hint: "in the audit trail" },
  ];
  return (
    <PhaseSection title="Closed" description="The mission is complete. Every figure below comes from deterministic computations.">
      <div className="flex items-center gap-3 rounded-xl border border-ok/30 bg-ok-soft p-4 text-ok">
        <BadgeCheck className="size-5" aria-hidden />
        <p className="text-sm font-medium">From the first email to the last invoice: {mission.ref} is closed.</p>
      </div>
      <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3" data-testid="summary">
        {stats.map((stat) => (
          <div key={stat.label} className="rounded-xl border bg-card p-4">
            <dt className="text-sm text-muted-foreground">{stat.label}</dt>
            <dd className="mt-1 text-2xl font-semibold tabular-nums">{stat.value}</dd>
            {stat.hint && <dd className="text-xs text-muted-foreground">{stat.hint}</dd>}
          </div>
        ))}
      </dl>
    </PhaseSection>
  );
}
