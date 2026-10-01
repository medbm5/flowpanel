"use client";

import { Inbox, RefreshCw } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { errorMessage } from "@/lib/api/client";
import { type BoardFilter, useMissions } from "@/lib/missions";
import { MissionRow } from "./mission-row";
import { NewMissionDialog } from "./new-mission-dialog";

const FILTERS: { value: BoardFilter; label: string }[] = [
  { value: "all", label: "All" },
  { value: "open", label: "In progress" },
  { value: "review", label: "Needs review" },
  { value: "closed", label: "Closed" },
];

const EMPTY: Record<BoardFilter, string> = {
  all: "No mission yet. Create one from a template or paste the email you received.",
  open: "Nothing in progress. Create a mission to get started.",
  review: "Nothing needs your review right now.",
  closed: "No closed mission yet.",
};

export function MissionBoard() {
  const [filter, setFilter] = useState<BoardFilter>("all");
  const missions = useMissions(filter);

  return (
    <section aria-labelledby="board-title">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 id="board-title" className="text-2xl font-semibold tracking-tight">
            Missions
          </h1>
          <p className="text-sm text-muted-foreground">Every mission runs through six gated phases, independently.</p>
        </div>
        <NewMissionDialog />
      </div>

      <Tabs value={filter} onValueChange={(v) => setFilter(v as BoardFilter)} className="mt-5">
        <TabsList aria-label="Filter missions">
          {FILTERS.map((f) => (
            <TabsTrigger key={f.value} value={f.value}>
              {f.label}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      <div className="mt-4">
        {missions.isLoading && (
          <ul className="grid gap-3" aria-busy="true" aria-label="Loading missions">
            {Array.from({ length: 3 }).map((_, i) => (
              <li key={i}>
                <Skeleton className="h-24 rounded-xl" />
              </li>
            ))}
          </ul>
        )}
        {missions.isError && (
          <div role="alert" className="flex items-center justify-between gap-3 rounded-xl border border-bad/30 bg-bad-soft p-4 text-sm text-bad">
            <span>{errorMessage(missions.error)}</span>
            <Button variant="outline" size="sm" onClick={() => missions.refetch()}>
              <RefreshCw aria-hidden /> Retry
            </Button>
          </div>
        )}
        {missions.data && missions.data.length === 0 && (
          <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed p-10 text-center">
            <Inbox className="size-8 text-muted-foreground" aria-hidden />
            <p className="max-w-sm text-sm text-muted-foreground">{EMPTY[filter]}</p>
            {filter !== "review" && filter !== "closed" && <NewMissionDialog />}
          </div>
        )}
        {missions.data && missions.data.length > 0 && (
          <ul className="grid gap-3" data-testid="mission-list">
            {missions.data.map((m) => (
              <MissionRow key={m.id} mission={m} />
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
