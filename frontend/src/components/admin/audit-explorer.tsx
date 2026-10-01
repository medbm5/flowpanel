"use client";

import { useQuery } from "@tanstack/react-query";
import { useDeferredValue, useState } from "react";
import { StatusPill } from "@/components/mission/status-badges";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { api, call } from "@/lib/api/client";
import type { AuditRow } from "@/lib/api/types";
import { dateTime } from "@/lib/format";

/** Audit log across tenants, filterable by mission, actor and AI vs human. */
export function AuditExplorer() {
  const [mission, setMission] = useState("");
  const [actor, setActor] = useState("");
  const [kind, setKind] = useState<"" | "AI" | "HUMAN" | "SYSTEM">("");
  const filters = useDeferredValue({ mission, actor, kind });
  const rows = useQuery({
    queryKey: ["admin", "audit", filters],
    queryFn: () =>
      call<AuditRow[]>(
        api.GET("/admin/audit", {
          params: { query: { mission: filters.mission || undefined, actor: filters.actor || undefined, kind: filters.kind || undefined, limit: 100 } },
        }),
      ),
  });

  return (
    <section className="rounded-xl border bg-card" aria-labelledby="audit-title">
      <div className="flex flex-wrap items-end gap-3 p-4">
        <h2 id="audit-title" className="mr-auto text-sm font-semibold">
          Audit log
        </h2>
        <label className="grid gap-1 text-xs text-muted-foreground">
          Mission
          <Input value={mission} onChange={(e) => setMission(e.target.value)} placeholder="ORD-2026-…" className="h-8 w-40" />
        </label>
        <label className="grid gap-1 text-xs text-muted-foreground">
          Actor
          <Input value={actor} onChange={(e) => setActor(e.target.value)} placeholder="Claire…" className="h-8 w-36" />
        </label>
        <label className="grid gap-1 text-xs text-muted-foreground">
          Kind
          <select
            value={kind}
            onChange={(e) => setKind(e.target.value as typeof kind)}
            className="h-8 rounded-lg border bg-background px-2 text-sm text-foreground"
            data-testid="audit-kind"
          >
            <option value="">All</option>
            <option value="AI">AI</option>
            <option value="HUMAN">Human</option>
            <option value="SYSTEM">System</option>
          </select>
        </label>
      </div>
      <div className="overflow-x-auto border-t">
        {rows.isLoading ? (
          <Skeleton className="m-4 h-24" />
        ) : (
          <table className="w-full min-w-[720px] text-sm" data-testid="audit-table">
            <thead className="text-xs text-muted-foreground">
              <tr className="border-b">
                <th scope="col" className="px-4 py-2 text-left font-medium">When</th>
                <th scope="col" className="px-3 py-2 text-left font-medium">Tenant · mission</th>
                <th scope="col" className="px-3 py-2 text-left font-medium">Actor</th>
                <th scope="col" className="px-4 py-2 text-left font-medium">Event</th>
              </tr>
            </thead>
            <tbody>
              {rows.data?.map((r) => (
                <tr key={r.id} className="border-b last:border-0">
                  <td className="whitespace-nowrap px-4 py-2 text-xs text-muted-foreground">{dateTime(r.createdAt)}</td>
                  <td className="px-3 py-2 text-xs">
                    {r.tenant ?? "—"} {r.missionRef && <span className="font-mono text-muted-foreground">· {r.missionRef}</span>}
                  </td>
                  <td className="px-3 py-2">
                    <StatusPill tone={r.actorKind === "AI" ? "cobalt" : r.actorKind === "HUMAN" ? "ok" : "neutral"}>{r.actorKind}</StatusPill>{" "}
                    <span className="text-xs">{r.actorName}</span>
                  </td>
                  <td className="px-4 py-2">{r.summary}</td>
                </tr>
              ))}
              {rows.data?.length === 0 && (
                <tr>
                  <td colSpan={4} className="px-4 py-6 text-center text-sm text-muted-foreground">
                    No event matches these filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}
