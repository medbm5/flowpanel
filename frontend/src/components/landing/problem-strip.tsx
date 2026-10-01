"use client";

import { m, type MotionValue, useReducedMotion, useScroll, useTransform } from "framer-motion";
import { FileText, Mail, Phone, Table2 } from "lucide-react";
import { useRef } from "react";
import { MissionRowFragment } from "./fragments";

const CHIPS = [
  { icon: Mail, label: "Re: caristes lundi ?", x: -42, y: -36 },
  { icon: FileText, label: "contrat_v3_final.pdf", x: 38, y: -40 },
  { icon: Table2, label: "heures_sem39.xlsx", x: -46, y: 6 },
  { icon: Mail, label: "Fwd: dispo intérimaires", x: 44, y: 2 },
  { icon: FileText, label: "facture_0923.pdf", x: -30, y: 40 },
  { icon: Phone, label: "Appel manqué — agence", x: 32, y: 42 },
  { icon: Mail, label: "URGENT remplacement", x: -8, y: -50 },
  { icon: Table2, label: "planning_quai.xlsx", x: 6, y: 50 },
  { icon: Mail, label: "Re: Re: avoir ?", x: -52, y: -12 },
  { icon: FileText, label: "CACES_scan.jpg", x: 50, y: -16 },
];

function Chip({ chip, progress, reduced }: { chip: (typeof CHIPS)[number]; progress: MotionValue<number>; reduced: boolean }) {
  const x = useTransform(progress, [0, 0.55], [`${chip.x * 9}%`, "0%"]);
  const y = useTransform(progress, [0, 0.55], [`${chip.y * 6}%`, "0%"]);
  const opacity = useTransform(progress, [0.35, 0.6], [1, 0]);
  const Icon = chip.icon;
  return (
    <m.span
      className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border bg-card px-3 py-1.5 text-xs text-muted-foreground shadow-sm"
      style={reduced ? { opacity: 0 } : { x, y, opacity }}
      aria-hidden
    >
      <Icon className="size-3.5" />
      {chip.label}
    </m.span>
  );
}

/** "Ten inboxes": scattered emails and files converge into a single mission row as the section scrolls by. */
export function ProblemStrip() {
  const ref = useRef<HTMLElement>(null);
  const reduced = !!useReducedMotion();
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start end", "end center"] });
  const rowOpacity = useTransform(scrollYProgress, [0.45, 0.65], [0, 1]);
  const rowScale = useTransform(scrollYProgress, [0.45, 0.7], [0.92, 1]);

  return (
    <section ref={ref} className="border-y bg-card/40 py-24 sm:py-32" aria-labelledby="problem-title">
      <div className="mx-auto grid max-w-7xl items-center gap-12 px-4 sm:px-8 lg:grid-cols-2">
        <div>
          <h2 id="problem-title" className="text-3xl font-semibold tracking-tight sm:text-5xl">
            Ten inboxes for one mission.
          </h2>
          <p className="mt-5 max-w-lg text-lg leading-relaxed text-muted-foreground">
            A request arrives by email, candidates by phone, contracts as PDFs, hours in spreadsheets, invoices by post. Nobody sees the
            whole mission — so double bookings, missing certificates and overbilled hours slip through.
          </p>
          <p className="mt-4 max-w-lg text-lg leading-relaxed">Flowpanel turns all of it into one mission, with one owner and one next action.</p>
        </div>
        <div className="relative h-[340px] overflow-hidden sm:h-[380px]">
          {CHIPS.map((c) => (
            <Chip key={c.label} chip={c} progress={scrollYProgress} reduced={reduced} />
          ))}
          <m.div
            className="absolute left-1/2 top-1/2 w-[min(360px,90%)] -translate-x-1/2 -translate-y-1/2"
            style={reduced ? undefined : { opacity: rowOpacity, scale: rowScale }}
          >
            <MissionRowFragment />
          </m.div>
        </div>
      </div>
    </section>
  );
}
