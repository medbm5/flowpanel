import type { PhaseStep } from "@/lib/api/types";
import { cn } from "@/lib/utils";

/** Six-segment progress bar, one segment per phase (done / active / locked). */
export function PhaseProgress({ phases, className }: { phases: PhaseStep[]; className?: string }) {
  const active = phases.find((p) => p.state === "ACTIVE");
  const done = phases.filter((p) => p.state === "DONE").length;
  const label = active ? `Phase ${done + 1} of 6: ${active.label}` : `${done} of 6 phases done`;
  return (
    <div className={cn("flex items-center gap-1", className)} role="img" aria-label={label} title={label}>
      {phases.map((p) => (
        <span
          key={p.phase}
          data-state={p.state}
          className={cn(
            "h-1.5 flex-1 rounded-full",
            p.state === "DONE" && "bg-cobalt",
            p.state === "ACTIVE" && "bg-cobalt/40 ring-1 ring-cobalt/60",
            p.state === "LOCKED" && "bg-muted-foreground/15",
          )}
        />
      ))}
    </div>
  );
}
