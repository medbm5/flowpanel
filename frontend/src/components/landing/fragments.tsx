import { AlertTriangle, Check, CheckCircle2, Circle, MapPin, Sparkles, XCircle } from "lucide-react";
import { cn } from "@/lib/utils";

/** Real UI fragments, rendered as components (no screenshots), reused by the hero and the sections. */

export function FragmentCard({ className, children }: { className?: string; children: React.ReactNode }) {
  return <div className={cn("rounded-2xl border bg-card/95 p-4 shadow-[0_20px_60px_-24px_rgba(15,23,42,0.35)] backdrop-blur", className)}>{children}</div>;
}

export function MissionRowFragment({ className }: { className?: string }) {
  return (
    <FragmentCard className={className}>
      <div className="flex items-center gap-2">
        <span className="font-mono text-[11px] text-muted-foreground">ORD-2026-0142</span>
        <span className="inline-flex items-center gap-1 rounded-full border border-warn/30 bg-warn-soft px-1.5 py-px text-[10px] font-medium text-warn">
          <AlertTriangle className="size-2.5" aria-hidden /> Needs review
        </span>
      </div>
      <p className="mt-1 text-sm font-medium">Caristes CACES 3 — Lesquin</p>
      <div className="mt-3 flex gap-1" aria-hidden>
        {[2, 2, 2, 1, 0, 0].map((s, i) => (
          <span key={i} className={cn("h-1.5 flex-1 rounded-full", s === 2 ? "bg-cobalt" : s === 1 ? "bg-cobalt/40" : "bg-muted-foreground/15")} />
        ))}
      </div>
      <p className="mt-2 text-xs text-muted-foreground">Timesheets · 2/2 positions · Resolve 1 anomaly</p>
    </FragmentCard>
  );
}

export function GateFragment({ className }: { className?: string }) {
  const checks = [
    { label: "Contracts generated (2/2)", ok: true },
    { label: "No blocking compliance issue", ok: true },
    { label: "All contracts signed", ok: false },
  ];
  return (
    <FragmentCard className={className}>
      <p className="text-xs font-semibold">Contracts gate</p>
      <ul className="mt-2 grid gap-1.5">
        {checks.map((c) => (
          <li key={c.label} className="flex items-center gap-2 text-xs">
            {c.ok ? <CheckCircle2 className="size-3.5 text-ok" aria-hidden /> : <Circle className="size-3.5 text-muted-foreground" aria-hidden />}
            <span className={cn(!c.ok && "text-muted-foreground")}>{c.label}</span>
          </li>
        ))}
      </ul>
      <div className="mt-3 h-7 rounded-md bg-muted text-center text-[11px] font-medium leading-7 text-muted-foreground">Finalize contracts</div>
    </FragmentCard>
  );
}

export function CandidateFragment({ className }: { className?: string }) {
  return (
    <FragmentCard className={className}>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm font-medium">
            <span className="mr-1 text-[11px] text-muted-foreground">#1</span>Julien M.
          </p>
          <p className="flex items-center gap-1 text-[11px] text-muted-foreground">
            InterSud Intérim · <MapPin className="size-2.5" aria-hidden /> Villeneuve-d&apos;Ascq
          </p>
        </div>
        <span className="text-right text-lg font-semibold leading-none tabular-nums">
          82<span className="block text-[9px] font-normal uppercase tracking-wide text-muted-foreground">score</span>
        </span>
      </div>
      <ul className="mt-2 grid gap-1 text-xs">
        <li className="flex gap-1.5"><Check className="size-3.5 text-ok" aria-hidden /> Holds CACES R489 cat. 3</li>
        <li className="flex gap-1.5"><Check className="size-3.5 text-ok" aria-hidden /> Available for the full period</li>
        <li className="flex gap-1.5"><Check className="size-3.5 text-ok" aria-hidden /> 6 km from the site</li>
        <li className="flex gap-1.5 text-muted-foreground"><XCircle className="size-3.5 text-warn" aria-hidden /> 4 years of experience</li>
      </ul>
    </FragmentCard>
  );
}

export function ExcludedFragment({ className }: { className?: string }) {
  return (
    <FragmentCard className={className}>
      <p className="text-sm font-medium">Karim H.</p>
      <p className="mt-1 flex items-center gap-1.5 text-xs text-bad">
        <XCircle className="size-3.5" aria-hidden /> Already placed on ORD-2026-0142
      </p>
    </FragmentCard>
  );
}

export function MatchRowFragment({ className }: { className?: string }) {
  return (
    <FragmentCard className={className}>
      <p className="text-xs font-semibold">Three-way match · INV-INTERSUD-0150</p>
      <div className="mt-2 grid grid-cols-[1fr_auto_auto] gap-x-3 gap-y-1 text-xs tabular-nums">
        <span className="text-muted-foreground">Approved</span>
        <span>105 h</span>
        <span>€1,386.00</span>
        <span className="text-muted-foreground">Invoiced</span>
        <span className="font-semibold text-warn">108 h</span>
        <span>€1,425.60</span>
      </div>
      <p className="mt-2 inline-flex items-center gap-1 rounded-full border border-warn/30 bg-warn-soft px-2 py-0.5 text-[11px] text-warn">
        Hours mismatch · credit note €39.60
      </p>
    </FragmentCard>
  );
}

export function AiDraftFragment({ className }: { className?: string }) {
  return (
    <FragmentCard className={className}>
      <p className="flex items-center gap-1.5 text-xs font-semibold">
        Message to the supplier
        <span className="inline-flex items-center gap-0.5 rounded border border-cobalt/25 bg-cobalt-soft px-1 py-px text-[9px] font-semibold text-cobalt">
          <Sparkles className="size-2" aria-hidden /> AI DRAFT
        </span>
      </p>
      <p className="mt-2 text-xs leading-relaxed text-muted-foreground">
        Bonjour, après rapprochement de votre facture avec les heures validées, nous constatons un écart de 3 h. Pourriez-vous nous adresser
        un avoir de 39,60 € HT ?
      </p>
    </FragmentCard>
  );
}
