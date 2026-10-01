import { ChevronRight, MapPin, Users } from "lucide-react";
import Link from "next/link";
import type { MissionSummary } from "@/lib/api/types";
import { PhaseProgress } from "./phase-progress";
import { NeedsReviewBadge, StatusPill } from "./status-badges";

export function MissionRow({ mission }: { mission: MissionSummary }) {
  const total = mission.positions.total;
  const closed = mission.phase === "CLOSED";
  return (
    <li data-testid="mission-row">
      <Link
        href={`/app/missions/${mission.id}`}
        className="group grid gap-3 rounded-xl border bg-card p-4 transition-colors hover:border-cobalt/40 hover:bg-accent/30 md:grid-cols-[minmax(0,2.2fr)_minmax(0,1.3fr)_minmax(0,1.6fr)_auto] md:items-center"
      >
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs text-muted-foreground">{mission.ref}</span>
            {mission.needsReview && <NeedsReviewBadge />}
            {closed && <StatusPill tone="ok">Closed</StatusPill>}
          </div>
          <p className="mt-1 truncate font-medium">{mission.title}</p>
          <p className="mt-0.5 flex items-center gap-1 truncate text-sm text-muted-foreground">
            <MapPin className="size-3.5 shrink-0" aria-hidden />
            {mission.site ?? "Site to be confirmed"}
          </p>
        </div>
        <div>
          <PhaseProgress phases={mission.phases} />
          <p className="mt-1.5 text-xs text-muted-foreground">
            {mission.phases.find((p) => p.state === "ACTIVE")?.label ?? "All phases done"}
          </p>
        </div>
        <div className="flex items-center justify-between gap-3 md:block">
          <p className="flex items-center gap-1.5 text-sm">
            <Users className="size-3.5 text-muted-foreground" aria-hidden />
            <span>
              {mission.positions.filled}/{total ?? "?"} <span className="text-muted-foreground">positions</span>
            </span>
          </p>
          <p className="mt-0.5 truncate text-sm text-muted-foreground" title={mission.nextAction}>
            {mission.nextAction}
          </p>
        </div>
        <ChevronRight className="hidden size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5 md:block" aria-hidden />
      </Link>
    </li>
  );
}
