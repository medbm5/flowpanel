"use client";

import { Ban, CheckCircle2, ChevronDown, Loader2, MapPin, Send, UserCheck, UserMinus, XCircle } from "lucide-react";
import { useState } from "react";
import { AiBadge, StatusPill } from "@/components/mission/status-badges";
import { Button } from "@/components/ui/button";
import { api, call } from "@/lib/api/client";
import type { CandidateView, SourcingView } from "@/lib/api/types";
import { phaseKeys, usePhaseAction, useSourcing } from "@/lib/mission-hooks";
import { cn } from "@/lib/utils";
import { PhaseError, PhaseLoading, PhaseSection } from "../phase-section";

export function SourcingPhase({ missionId }: { missionId: number }) {
  const sourcing = useSourcing(missionId);
  const publish = usePhaseAction(missionId, phaseKeys.sourcing(missionId), () =>
    call<SourcingView>(api.POST("/missions/{id}/sourcing/publish", { params: { path: { id: missionId } } })),
    (v) => `Order published to ${v.suppliers.length} suppliers`,
  );
  const [showExcluded, setShowExcluded] = useState(true);
  if (sourcing.isLoading) return <PhaseLoading />;
  if (sourcing.isError || !sourcing.data) return <PhaseError error={sourcing.error} />;
  const view = sourcing.data;

  return (
    <PhaseSection
      title="Sourcing"
      description="Rules exclude candidates first (certification, availability, double booking); the rest are ranked by profile match, distance and experience. You select."
      readOnly={view.readOnly}
      actions={
        !view.published && (
          <Button onClick={() => publish.mutate(undefined)} disabled={publish.isPending}>
            {publish.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <Send aria-hidden />}
            Publish to panel
          </Button>
        )
      }
    >
      {!view.published ? (
        <p className="rounded-xl border border-dashed p-6 text-sm text-muted-foreground">
          The order has not been sent yet. Publishing sends it to every supplier of your panel, who propose candidates.
        </p>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <StatusPill tone={view.filled >= view.quantity ? "ok" : "cobalt"}>
              {view.filled}/{view.quantity} positions filled
            </StatusPill>
            <span className="text-muted-foreground">Sent to {view.suppliers.join(", ")}</span>
          </div>
          <ul className="grid gap-3 lg:grid-cols-2" data-testid="candidates">
            {view.candidates.map((c) => (
              <CandidateCard key={c.candidateId} missionId={missionId} candidate={c} readOnly={view.readOnly} full={view.filled >= view.quantity} />
            ))}
          </ul>
          {view.excluded.length > 0 && (
            <div className="rounded-xl border bg-card">
              <button
                type="button"
                className="flex w-full items-center justify-between px-4 py-3 text-sm font-medium"
                aria-expanded={showExcluded}
                onClick={() => setShowExcluded((v) => !v)}
              >
                <span className="flex items-center gap-2">
                  <Ban className="size-4 text-bad" aria-hidden /> {view.excluded.length} excluded by the rules
                </span>
                <ChevronDown className={cn("size-4 transition-transform", showExcluded && "rotate-180")} aria-hidden />
              </button>
              {showExcluded && (
                <ul className="divide-y border-t" data-testid="excluded">
                  {view.excluded.map((c) => (
                    <li key={c.candidateId} className="grid gap-1 px-4 py-2.5 text-sm sm:grid-cols-[200px_minmax(0,1fr)]">
                      <span>
                        <span className="font-medium">{c.workerName}</span>
                        <span className="block text-xs text-muted-foreground">{c.supplierName}</span>
                      </span>
                      <span className="grid gap-0.5">
                        {c.exclusionReasons.map((r) => (
                          <span key={r} className="flex items-start gap-1.5 text-bad">
                            <XCircle className="mt-0.5 size-3.5 shrink-0" aria-hidden /> {r}
                          </span>
                        ))}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </>
      )}
    </PhaseSection>
  );
}

function CandidateCard({ missionId, candidate: c, readOnly, full }: { missionId: number; candidate: CandidateView; readOnly: boolean; full: boolean }) {
  const toggle = usePhaseAction(
    missionId,
    phaseKeys.sourcing(missionId),
    (select: boolean) =>
      call<SourcingView>(
        select
          ? api.POST("/missions/{id}/sourcing/select/{candidateId}", { params: { path: { id: missionId, candidateId: c.candidateId } } })
          : api.DELETE("/missions/{id}/sourcing/select/{candidateId}", { params: { path: { id: missionId, candidateId: c.candidateId } } }),
      ),
    (_, select) => (select ? `${c.workerName} selected` : `${c.workerName} removed`),
  );
  return (
    <li
      className={cn("flex flex-col gap-3 rounded-xl border bg-card p-4", c.selected && "border-ok/50 ring-1 ring-ok/30")}
      data-testid="candidate-card"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-medium">
            <span className="mr-1.5 text-xs tabular-nums text-muted-foreground">#{c.rank}</span>
            {c.workerName}
          </p>
          <p className="flex items-center gap-1 text-xs text-muted-foreground">
            {c.supplierName} · <MapPin className="size-3" aria-hidden /> {c.city}
          </p>
        </div>
        <span className="text-right">
          <span className="block text-lg font-semibold tabular-nums leading-none">{c.score.toFixed(0)}</span>
          <span className="text-[10px] uppercase tracking-wide text-muted-foreground">score</span>
        </span>
      </div>
      <ul className="grid gap-1 text-sm">
        {c.explanation.map((item) => (
          <li key={item.text} className="flex items-start gap-1.5">
            {item.ok ? (
              <CheckCircle2 className="mt-0.5 size-3.5 shrink-0 text-ok" aria-label="Meets" />
            ) : (
              <XCircle className="mt-0.5 size-3.5 shrink-0 text-warn" aria-label="Does not meet" />
            )}
            <span className={cn(!item.ok && "text-muted-foreground")}>{item.text}</span>
          </li>
        ))}
      </ul>
      {c.aiSummary && (
        <p className="flex items-start gap-2 rounded-lg bg-cobalt-soft/60 px-3 py-2 text-sm">
          <AiBadge className="mt-0.5 shrink-0" />
          <span>{c.aiSummary}</span>
        </p>
      )}
      {!readOnly && (
        <div className="mt-auto">
          {c.selected ? (
            <Button variant="outline" size="sm" onClick={() => toggle.mutate(false)} disabled={toggle.isPending}>
              <UserMinus aria-hidden /> Unselect
            </Button>
          ) : (
            <Button size="sm" onClick={() => toggle.mutate(true)} disabled={toggle.isPending || full}>
              <UserCheck aria-hidden /> Select
            </Button>
          )}
        </div>
      )}
      {readOnly && c.selected && <StatusPill tone="ok">Placed</StatusPill>}
    </li>
  );
}
