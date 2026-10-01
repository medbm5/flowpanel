"use client";

import { Bot, FileText, Settings2, User } from "lucide-react";
import { useState } from "react";
import { StatusPill } from "@/components/mission/status-badges";
import { Skeleton } from "@/components/ui/skeleton";
import type { ArtifactView, AuditView } from "@/lib/api/types";
import { dateTime, PHASE_LABEL } from "@/lib/format";
import { useAudit } from "@/lib/mission-hooks";
import { cn } from "@/lib/utils";

const ARTIFACT_LABEL: Record<string, string> = {
  ORDER: "Order",
  SHORTLIST: "Shortlist",
  CONTRACT: "Contract",
  TIMESHEETS: "Timesheets",
  INVOICE: "Invoice",
  CREDIT_NOTE: "Credit note",
  SUMMARY: "Summary",
};

function tone(status: string) {
  if (["CONFIRMED", "FINAL", "SIGNED", "APPROVED", "MATCHED", "RECEIVED"].includes(status)) return "ok" as const;
  if (status === "MISMATCH") return "warn" as const;
  return "neutral" as const;
}

export function SidePanel({ missionId, artifacts }: { missionId: number; artifacts: ArtifactView[] }) {
  const audit = useAudit(missionId);
  const [filter, setFilter] = useState<"all" | "AI" | "HUMAN">("all");
  const events = (audit.data ?? []).filter((e) => filter === "all" || e.actorKind === filter);

  return (
    <aside className="grid gap-4" aria-label="Artifacts and audit trail">
      <section className="rounded-xl border bg-card p-4">
        <h2 className="text-sm font-semibold">Artifacts</h2>
        {artifacts.length === 0 ? (
          <p className="mt-2 text-sm text-muted-foreground">Documents produced by each phase appear here.</p>
        ) : (
          <ul className="mt-2 grid gap-2" data-testid="artifacts">
            {artifacts.map((a) => (
              <li key={a.id} className="flex items-center justify-between gap-2 text-sm">
                <span className="flex min-w-0 items-center gap-2">
                  <FileText className="size-3.5 shrink-0 text-muted-foreground" aria-hidden />
                  <span className="min-w-0">
                    <span className="block truncate">
                      {ARTIFACT_LABEL[a.type] ?? a.type}{" "}
                      {typeof a.payload?.pdf === "string" ? (
                        <a className="font-mono text-xs text-cobalt underline-offset-2 hover:underline" href={`/api${a.payload.pdf}`} target="_blank" rel="noreferrer">
                          {a.ref}
                        </a>
                      ) : (
                        <span className="font-mono text-xs text-muted-foreground">{a.ref}</span>
                      )}
                    </span>
                    <span className="block text-xs text-muted-foreground">{PHASE_LABEL[a.phase]}</span>
                  </span>
                </span>
                <StatusPill tone={tone(a.status)}>{a.status.toLowerCase()}</StatusPill>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="rounded-xl border bg-card p-4">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-sm font-semibold">Audit trail</h2>
          <div className="flex rounded-md border p-0.5 text-xs" role="group" aria-label="Filter audit events">
            {(["all", "AI", "HUMAN"] as const).map((f) => (
              <button
                key={f}
                type="button"
                aria-pressed={filter === f}
                onClick={() => setFilter(f)}
                className={cn("rounded px-1.5 py-0.5", filter === f ? "bg-muted font-medium" : "text-muted-foreground")}
              >
                {f === "all" ? "All" : f === "AI" ? "AI" : "People"}
              </button>
            ))}
          </div>
        </div>
        {audit.isLoading ? (
          <Skeleton className="mt-3 h-24" />
        ) : (
          <ol className="mt-3 grid max-h-[420px] gap-3 overflow-y-auto pr-1" data-testid="audit-trail">
            {events.map((e) => (
              <AuditItem key={e.id} event={e} />
            ))}
            {events.length === 0 && <li className="text-sm text-muted-foreground">No event yet.</li>}
          </ol>
        )}
      </section>
    </aside>
  );
}

function AuditItem({ event }: { event: AuditView }) {
  const Icon = event.actorKind === "AI" ? Bot : event.actorKind === "HUMAN" ? User : Settings2;
  return (
    <li className="flex gap-2 text-sm">
      <span
        className={cn(
          "mt-0.5 grid size-6 shrink-0 place-items-center rounded-full",
          event.actorKind === "AI" ? "bg-cobalt-soft text-cobalt" : event.actorKind === "HUMAN" ? "bg-ok-soft text-ok" : "bg-muted text-muted-foreground",
        )}
        aria-hidden
      >
        <Icon className="size-3.5" />
      </span>
      <span className="min-w-0">
        <span className="block leading-snug">{event.summary}</span>
        <span className="block text-xs text-muted-foreground">
          {event.actorName} · {dateTime(event.createdAt)}
        </span>
      </span>
    </li>
  );
}
