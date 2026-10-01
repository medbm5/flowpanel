import { Lock } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { errorMessage } from "@/lib/api/client";

/** Frame of a phase workspace: title, explanation, primary actions, read-only notice. */
export function PhaseSection({
  title,
  description,
  actions,
  readOnly,
  children,
}: {
  title: string;
  description: string;
  actions?: React.ReactNode;
  readOnly?: boolean;
  children: React.ReactNode;
}) {
  return (
    <section className="grid gap-4" aria-labelledby="phase-title">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 id="phase-title" className="text-lg font-semibold tracking-tight">
            {title}
          </h2>
          <p className="text-sm text-muted-foreground">{description}</p>
        </div>
        {!readOnly && actions && <div className="flex flex-wrap gap-2">{actions}</div>}
      </div>
      {readOnly && (
        <p className="flex items-center gap-2 rounded-lg border bg-muted/50 px-3 py-2 text-xs text-muted-foreground">
          <Lock className="size-3.5" aria-hidden /> This phase is finalized and read-only.
        </p>
      )}
      {children}
    </section>
  );
}

export function PhaseLoading() {
  return (
    <div className="grid gap-3" aria-busy="true">
      <Skeleton className="h-8 w-60" />
      <Skeleton className="h-40 rounded-xl" />
    </div>
  );
}

export function PhaseError({ error }: { error: unknown }) {
  return (
    <p role="alert" className="rounded-xl border border-bad/30 bg-bad-soft p-4 text-sm text-bad">
      {errorMessage(error)}
    </p>
  );
}

export function ConfidenceBar({ value }: { value: number }) {
  const pct = Math.round(value * 100);
  const tone = value >= 0.75 ? "bg-ok" : value >= 0.5 ? "bg-warn" : "bg-bad";
  return (
    <span className="inline-flex items-center gap-1.5" title={`AI confidence ${pct}%`}>
      <span className="h-1.5 w-14 overflow-hidden rounded-full bg-muted" aria-hidden>
        <span className={`block h-full ${tone}`} style={{ width: `${pct}%` }} />
      </span>
      <span className="text-xs tabular-nums text-muted-foreground">{pct}%</span>
    </span>
  );
}
