"use client";

import { AnimatePresence, m, useMotionValueEvent, useReducedMotion, useScroll } from "framer-motion";
import { Check } from "lucide-react";
import { useRef, useState } from "react";
import { cn } from "@/lib/utils";
import { AiDraftFragment, CandidateFragment, ExcludedFragment, FragmentCard, GateFragment, MatchRowFragment, MissionRowFragment } from "./fragments";
import { useMedia } from "./use-media";

const STEPS = [
  {
    phase: "Intake",
    title: "The email becomes an order",
    body: "The AI extracts position, dates, rate and legal reason with a confidence per field. Anything hedged or invalid is flagged for you to confirm.",
    visual: <IntakeVisual />,
  },
  {
    phase: "Sourcing",
    title: "Explainable shortlists",
    body: "Hard rules exclude first — missing certificate, partial availability, already placed elsewhere. Then candidates are ranked, with every reason shown.",
    visual: (
      <div className="grid gap-3">
        <CandidateFragment />
        <ExcludedFragment />
      </div>
    ),
  },
  {
    phase: "Contracts",
    title: "Compliance checked before signature",
    body: "Each contract is drafted from the order and checked by deterministic rules: rate, legal reason, certificates, dates. Blocking issues get a one-click fix.",
    visual: <GateFragment />,
  },
  {
    phase: "Timesheets",
    title: "Anomalies explained, not hidden",
    body: "Hours above the contract or legal limits are flagged. The AI explains the daily breakdown; you approve the overtime or send the sheet back.",
    visual: <TimesheetVisual />,
  },
  {
    phase: "Invoice",
    title: "A three-way match on every line",
    body: "Contract rate × approved hours versus the supplier's PDF, to the cent. A mismatch comes with a drafted credit-note request.",
    visual: (
      <div className="grid gap-3">
        <MatchRowFragment />
        <AiDraftFragment />
      </div>
    ),
  },
  {
    phase: "Closed",
    title: "A mission you can audit",
    body: "Hours approved, amount paid, overbilling avoided — and every AI suggestion with the human decision that followed it.",
    visual: <MissionRowFragment />,
  },
];

export function HowItWorks() {
  const ref = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(0);
  const reduced = useReducedMotion();
  const wide = useMedia("(min-width: 1024px)");
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start start", "end end"] });
  useMotionValueEvent(scrollYProgress, "change", (v) => setActive(Math.min(STEPS.length - 1, Math.max(0, Math.floor(v * STEPS.length)))));

  return (
    <section id="how" aria-labelledby="how-title" className="py-24 sm:py-32">
      <div className="mx-auto max-w-7xl px-4 sm:px-8">
        <h2 id="how-title" className="max-w-3xl text-3xl font-semibold tracking-tight sm:text-5xl">
          Six phases. Each one opens only when the previous one is done.
        </h2>
        <p className="mt-5 max-w-2xl text-lg text-muted-foreground">
          Every mission runs on its own phase-gated track, so ten missions can move in parallel without anything skipping a step.
        </p>
      </div>

      {wide ? (
        <div ref={ref} className="relative mx-auto mt-10 max-w-7xl px-8" style={{ height: `${STEPS.length * 75}vh` }}>
          <div className="sticky top-0 grid h-screen grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] items-center gap-16">
            <ol className="grid gap-1" aria-label="Phases">
              {STEPS.map((s, i) => (
                <li key={s.phase}>
                  <div className={cn("flex gap-4 rounded-2xl p-4 transition-colors duration-300", i === active && "bg-card shadow-sm ring-1 ring-border")}>
                    <span
                      className={cn(
                        "grid size-8 shrink-0 place-items-center rounded-full border text-sm font-semibold transition-colors duration-300",
                        i < active && "border-cobalt bg-cobalt text-primary-foreground",
                        i === active && "border-cobalt text-cobalt",
                        i > active && "text-muted-foreground",
                      )}
                    >
                      {i < active ? <Check className="size-4" aria-hidden /> : i + 1}
                    </span>
                    <div>
                      <p className={cn("font-medium transition-colors", i === active ? "text-foreground" : "text-muted-foreground")}>
                        {s.phase} <span className="font-normal text-muted-foreground">— {s.title}</span>
                      </p>
                      <div className={cn("grid transition-[grid-template-rows] duration-300", i === active ? "grid-rows-[1fr]" : "grid-rows-[0fr]")}>
                        <p className="overflow-hidden text-sm leading-relaxed text-muted-foreground">{s.body}</p>
                      </div>
                    </div>
                  </div>
                </li>
              ))}
            </ol>
            <div className="relative h-[440px]">
              <AnimatePresence mode="wait" initial={false}>
                <m.div
                  key={active}
                  className="absolute inset-0 grid place-items-center"
                  initial={reduced ? false : { opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={reduced ? undefined : { opacity: 0, y: -12 }}
                  transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
                >
                  <div className="w-full max-w-md">{STEPS[active].visual}</div>
                </m.div>
              </AnimatePresence>
            </div>
          </div>
        </div>
      ) : (
        <ol className="mx-auto mt-12 grid max-w-2xl gap-10 px-4 sm:px-8">
          {STEPS.map((s, i) => (
            <li key={s.phase} className="grid gap-4">
              <div className="flex gap-3">
                <span className="grid size-8 shrink-0 place-items-center rounded-full border border-cobalt text-sm font-semibold text-cobalt">{i + 1}</span>
                <div>
                  <p className="font-medium">
                    {s.phase} — {s.title}
                  </p>
                  <p className="mt-1 text-sm leading-relaxed text-muted-foreground">{s.body}</p>
                </div>
              </div>
              {s.visual}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

function IntakeVisual() {
  const rows = [
    { label: "Position", value: "Cariste", c: 92 },
    { label: "Quantity", value: "2", c: 92 },
    { label: "Start date", value: "5 Oct 2026", c: 92 },
    { label: "End date", value: "16 Oct 2026 — “normalement”", c: 55, flag: true },
    { label: "Hourly rate", value: "€13.20", c: 92 },
  ];
  return (
    <FragmentCard>
      <p className="text-xs font-semibold">Order draft</p>
      <ul className="mt-2 divide-y text-xs">
        {rows.map((r) => (
          <li key={r.label} className={cn("grid grid-cols-[90px_1fr_auto] items-center gap-2 py-1.5", r.flag && "-mx-2 rounded bg-warn-soft px-2")}>
            <span className="text-muted-foreground">{r.label}</span>
            <span>{r.value}</span>
            <span className={cn("tabular-nums", r.flag ? "text-warn" : "text-muted-foreground")}>{r.c}%</span>
          </li>
        ))}
      </ul>
    </FragmentCard>
  );
}

function TimesheetVisual() {
  const days = ["8", "8", "8", "8.5", "8.5", "–", "–"];
  return (
    <FragmentCard>
      <p className="text-xs font-semibold">Karim H. · week of 21 Sep</p>
      <div className="mt-2 grid grid-cols-7 gap-1 text-center text-xs tabular-nums">
        {["M", "T", "W", "T", "F", "S", "S"].map((d, i) => (
          <span key={i} className="text-[10px] text-muted-foreground">
            {d}
          </span>
        ))}
        {days.map((h, i) => (
          <span key={i} className={cn("rounded py-1", i < 5 ? "bg-warn-soft font-semibold text-warn" : "text-muted-foreground")}>
            {h}
          </span>
        ))}
      </div>
      <p className="mt-3 text-xs">41 h worked vs 35 h contracted, no overtime agreed</p>
    </FragmentCard>
  );
}
