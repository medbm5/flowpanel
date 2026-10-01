"use client";

import { Check, Lock } from "lucide-react";
import { toast } from "sonner";
import type { Phase, PhaseStep } from "@/lib/api/types";
import { PHASE_LABEL } from "@/lib/format";
import { cn } from "@/lib/utils";

/** Done / active / locked phases. A locked phase cannot be opened: the toast names the phase to finalize first. */
export function PhaseRail({
  phases,
  current,
  selected,
  onSelect,
}: {
  phases: PhaseStep[];
  current: Phase;
  selected: Phase;
  onSelect: (phase: Phase) => void;
}) {
  return (
    <nav aria-label="Mission phases">
      <ol className="flex gap-2 overflow-x-auto pb-1 lg:flex-col lg:gap-1 lg:overflow-visible lg:pb-0">
        {phases.map((p, i) => {
          const locked = p.state === "LOCKED";
          const isSelected = p.phase === selected;
          return (
            <li key={p.phase} className="shrink-0">
              <button
                type="button"
                aria-current={isSelected ? "step" : undefined}
                aria-disabled={locked}
                data-testid={`rail-${p.phase}`}
                onClick={() => {
                  if (locked) {
                    toast.info(`${p.label} is locked`, { description: `Finalize ${PHASE_LABEL[current]} first.` });
                    return;
                  }
                  onSelect(p.phase);
                }}
                className={cn(
                  "flex w-full items-center gap-2.5 rounded-lg border border-transparent px-3 py-2 text-left text-sm transition-colors",
                  isSelected && "border-border bg-card shadow-xs",
                  !isSelected && !locked && "hover:bg-muted",
                  locked && "cursor-not-allowed text-muted-foreground",
                )}
              >
                <span
                  className={cn(
                    "grid size-6 shrink-0 place-items-center rounded-full border text-[11px] font-semibold",
                    p.state === "DONE" && "border-cobalt bg-cobalt text-primary-foreground",
                    p.state === "ACTIVE" && "border-cobalt text-cobalt",
                    locked && "border-border bg-muted",
                  )}
                  aria-hidden
                >
                  {p.state === "DONE" ? <Check className="size-3.5" /> : locked ? <Lock className="size-3" /> : i + 1}
                </span>
                <span className="min-w-0">
                  <span className="block font-medium">{p.label}</span>
                  <span className="block text-xs text-muted-foreground">
                    {p.state === "DONE" ? `Finalized${p.finalizedBy ? ` by ${p.finalizedBy.split(" ")[0]}` : ""}` : p.state === "ACTIVE" ? "In progress" : "Locked"}
                  </span>
                </span>
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
