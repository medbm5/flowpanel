"use client";

import { ArrowLeft, MapPin } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { NeedsReviewBadge, StatusPill } from "@/components/mission/status-badges";
import { Skeleton } from "@/components/ui/skeleton";
import type { Phase } from "@/lib/api/types";
import { useMission } from "@/lib/mission-hooks";
import { GatePanel } from "./gate-panel";
import { MissionSwitcher } from "./mission-switcher";
import { PhaseError } from "./phase-section";
import { PhaseRail } from "./phase-rail";
import { ClosedPhase } from "./phases/closed-phase";
import { ContractsPhase } from "./phases/contracts-phase";
import { IntakePhase } from "./phases/intake-phase";
import { InvoicePhase } from "./phases/invoice-phase";
import { SourcingPhase } from "./phases/sourcing-phase";
import { TimesheetsPhase } from "./phases/timesheets-phase";
import { SidePanel } from "./side-panel";

export function MissionWorkspace({ missionId }: { missionId: number }) {
  const mission = useMission(missionId);
  const [selected, setSelected] = useState<Phase | null>(null);

  if (mission.isLoading) {
    return (
      <div className="grid gap-4">
        <Skeleton className="h-12 w-80" />
        <Skeleton className="h-96 rounded-xl" />
      </div>
    );
  }
  if (mission.isError || !mission.data) return <PhaseError error={mission.error} />;
  const m = mission.data;
  const shown: Phase = selected ?? m.phase;

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <Link href="/app" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="size-3.5" aria-hidden /> Missions
          </Link>
          <div className="mt-1 flex flex-wrap items-center gap-2">
            <span className="font-mono text-sm text-muted-foreground">{m.ref}</span>
            {m.needsReview && <NeedsReviewBadge />}
            {m.closed && <StatusPill tone="ok">Closed</StatusPill>}
          </div>
          <h1 className="text-2xl font-semibold tracking-tight" data-testid="mission-title">
            {m.title}
          </h1>
          <p className="flex items-center gap-1 text-sm text-muted-foreground">
            <MapPin className="size-3.5" aria-hidden /> {m.site ?? "Site to be confirmed"} · {m.positions.filled}/{m.positions.total ?? "?"} positions
          </p>
        </div>
        <MissionSwitcher currentId={m.id} />
      </div>

      <div className="grid gap-5 lg:grid-cols-[210px_minmax(0,1fr)] xl:grid-cols-[210px_minmax(0,1fr)_300px]">
        <PhaseRail phases={m.phases} current={m.phase} selected={shown} onSelect={setSelected} />
        <div className="grid min-w-0 content-start gap-5" data-testid={`workspace-${shown}`}>
          {shown === "INTAKE" && <IntakePhase missionId={m.id} />}
          {shown === "SOURCING" && <SourcingPhase missionId={m.id} />}
          {shown === "CONTRACTS" && <ContractsPhase missionId={m.id} />}
          {shown === "TIMESHEETS" && <TimesheetsPhase missionId={m.id} />}
          {shown === "INVOICE" && <InvoicePhase missionId={m.id} />}
          {shown === "CLOSED" && <ClosedPhase mission={m} />}
          {shown === m.phase && <GatePanel mission={m} onFinalized={() => setSelected(null)} />}
        </div>
        <div className="lg:col-span-2 xl:col-span-1">
          <SidePanel missionId={m.id} artifacts={m.artifacts} />
        </div>
      </div>
    </div>
  );
}
