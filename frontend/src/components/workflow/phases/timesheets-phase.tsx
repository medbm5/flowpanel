"use client";

import { CheckCheck, ListChecks, Loader2, TriangleAlert, Undo2 } from "lucide-react";
import { AiBadge, StatusPill } from "@/components/mission/status-badges";
import { Button } from "@/components/ui/button";
import { api, call } from "@/lib/api/client";
import type { AnomalyView, TimesheetsView } from "@/lib/api/types";
import { formatHours, shortDate } from "@/lib/format";
import { phaseKeys, usePhaseAction, useTimesheets } from "@/lib/mission-hooks";
import { cn } from "@/lib/utils";
import { PhaseError, PhaseLoading, PhaseSection } from "../phase-section";

const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

export function TimesheetsPhase({ missionId }: { missionId: number }) {
  const sheets = useTimesheets(missionId);
  const check = usePhaseAction(missionId, phaseKeys.timesheets(missionId), () =>
    call<TimesheetsView>(api.POST("/missions/{id}/timesheets/check", { params: { path: { id: missionId } } })),
    (v) => {
      const open = v.anomalies.filter((a) => a.status === "OPEN").length;
      return open ? `${open} anomal${open === 1 ? "y" : "ies"} found` : "No anomaly found";
    },
  );
  const approve = usePhaseAction(missionId, phaseKeys.timesheets(missionId), () =>
    call<TimesheetsView>(api.POST("/missions/{id}/timesheets/approve", { params: { path: { id: missionId } } })),
    () => "Timesheets approved",
  );
  if (sheets.isLoading) return <PhaseLoading />;
  if (sheets.isError || !sheets.data) return <PhaseError error={sheets.error} />;
  const view = sheets.data;
  const open = view.anomalies.filter((a) => a.status === "OPEN");

  return (
    <PhaseSection
      title="Timesheets"
      description="Suppliers submit weekly hours. Rules flag hours above the contract or legal limits; the AI explains each anomaly; you decide."
      readOnly={view.readOnly}
      actions={
        <>
          <Button variant={view.checked ? "outline" : "default"} onClick={() => check.mutate(undefined)} disabled={check.isPending || view.approved}>
            {check.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <ListChecks aria-hidden />}
            {view.checked ? "Run checks again" : "Run checks"}
          </Button>
          {view.checked && !view.approved && (
            <Button onClick={() => approve.mutate(undefined)} disabled={approve.isPending || open.length > 0}>
              {approve.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <CheckCheck aria-hidden />}
              Approve timesheets
            </Button>
          )}
        </>
      }
    >
      {view.anomalies.length > 0 && (
        <ul className="grid gap-3" data-testid="anomalies">
          {view.anomalies.map((a) => (
            <AnomalyCallout key={a.id} missionId={missionId} anomaly={a} readOnly={view.readOnly} />
          ))}
        </ul>
      )}

      <div className="overflow-x-auto rounded-xl border bg-card">
        <table className="w-full min-w-[720px] text-sm" data-testid="timesheet-table">
          <caption className="sr-only">Weekly hours per worker</caption>
          <thead className="text-xs text-muted-foreground">
            <tr className="border-b">
              <th scope="col" className="px-3 py-2 text-left font-medium">Worker · week</th>
              {DAYS.map((d) => (
                <th key={d} scope="col" className="px-2 py-2 text-right font-medium">
                  {d}
                </th>
              ))}
              <th scope="col" className="px-3 py-2 text-right font-medium">Total</th>
              <th scope="col" className="px-3 py-2 text-right font-medium">Contract</th>
              <th scope="col" className="px-3 py-2 text-left font-medium">Status</th>
            </tr>
          </thead>
          <tbody>
            {view.timesheets.map((t) => {
              const over = Number(t.total) > Number(t.contractedHours);
              return (
                <tr key={t.id} className="border-b last:border-0">
                  <th scope="row" className="px-3 py-2 text-left font-normal">
                    <span className="block font-medium">{t.workerName}</span>
                    <span className="text-xs text-muted-foreground">Week of {shortDate(t.weekStart)}</span>
                  </th>
                  {t.dailyHours.map((h, i) => {
                    const flagged = t.flaggedDays.includes(i);
                    return (
                      <td
                        key={i}
                        className={cn("px-2 py-2 text-right tabular-nums", flagged && "bg-warn-soft font-semibold text-warn", Number(h) === 0 && "text-muted-foreground/60")}
                        data-flagged={flagged || undefined}
                      >
                        {Number(h) === 0 ? "–" : Number(h).toString()}
                      </td>
                    );
                  })}
                  <td className={cn("px-3 py-2 text-right font-medium tabular-nums", over && "text-warn")}>{formatHours(t.total)}</td>
                  <td className="px-3 py-2 text-right tabular-nums text-muted-foreground">{formatHours(t.contractedHours)}</td>
                  <td className="px-3 py-2">
                    <StatusPill tone={t.status === "APPROVED" ? "ok" : t.status === "CORRECTED" ? "cobalt" : "neutral"}>
                      {t.status.toLowerCase()}
                    </StatusPill>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {view.totals.length > 0 && (
        <ul className="flex flex-wrap gap-3 text-sm">
          {view.totals.map((w) => (
            <li key={w.workerId} className="rounded-lg border bg-card px-3 py-2">
              <span className="font-medium">{w.workerName}</span>{" "}
              <span className="text-muted-foreground">
                submitted {formatHours(w.submitted)}
                {w.approved !== null && w.approved !== undefined && <> · approved {formatHours(w.approved)}</>}
              </span>
            </li>
          ))}
        </ul>
      )}
    </PhaseSection>
  );
}

function AnomalyCallout({ missionId, anomaly: a, readOnly }: { missionId: number; anomaly: AnomalyView; readOnly: boolean }) {
  const resolve = usePhaseAction(
    missionId,
    phaseKeys.timesheets(missionId),
    (resolution: "APPROVE_OVERTIME" | "RETURN_TO_SUPPLIER") =>
      call<TimesheetsView>(api.POST("/anomalies/{anomalyId}/resolve", { params: { path: { anomalyId: a.id } }, body: { resolution } })),
    (_, r) => (r === "APPROVE_OVERTIME" ? "Overtime approved" : "Returned to the supplier — corrected sheet received"),
  );
  const isOpen = a.status === "OPEN";
  return (
    <li className={cn("rounded-xl border p-4", isOpen ? "border-warn/40 bg-warn-soft/50" : "bg-card")} data-testid="anomaly">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <p className="flex items-center gap-2 font-medium">
          <TriangleAlert className={cn("size-4", isOpen ? "text-warn" : "text-muted-foreground")} aria-hidden />
          {a.workerName} · week of {shortDate(a.weekStart)}
        </p>
        {!isOpen && (
          <StatusPill tone="ok">{a.resolution === "APPROVE_OVERTIME" ? "Overtime approved" : "Returned to supplier"}</StatusPill>
        )}
      </div>
      <p className="mt-1 text-sm">{a.message}</p>
      {a.aiExplanation && (
        <p className="mt-2 flex items-start gap-2 text-sm text-muted-foreground">
          <AiBadge className="mt-0.5 shrink-0" /> {a.aiExplanation}
        </p>
      )}
      {isOpen && !readOnly && (
        <div className="mt-3 flex flex-wrap gap-2">
          <Button size="sm" onClick={() => resolve.mutate("APPROVE_OVERTIME")} disabled={resolve.isPending}>
            <CheckCheck aria-hidden /> Approve overtime
          </Button>
          <Button size="sm" variant="outline" onClick={() => resolve.mutate("RETURN_TO_SUPPLIER")} disabled={resolve.isPending}>
            <Undo2 aria-hidden /> Return to supplier
          </Button>
        </div>
      )}
    </li>
  );
}
