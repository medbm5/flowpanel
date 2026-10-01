"use client";

import { animate, m, useInView, useMotionValue, useReducedMotion, useTransform } from "framer-motion";
import { ArrowRight, Bot, Eye, Lock, ScrollText, ShieldCheck, UserCheck } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef } from "react";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Button } from "@/components/ui/button";
import { AiDraftFragment, CandidateFragment, GateFragment } from "./fragments";

/** One orchestrated reveal per section, disabled under reduced motion. */
function Reveal({ children, className }: { children: React.ReactNode; className?: string }) {
  const reduced = useReducedMotion();
  return (
    <m.div
      className={className}
      initial={reduced ? false : { opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-80px" }}
      transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
    >
      {children}
    </m.div>
  );
}

export function Principles() {
  const items = [
    {
      title: "The AI drafts, rules decide",
      body: "Extraction, ranking explanations and messages come from the model. Compliance, matching, money and phase transitions are deterministic code with tests.",
      visual: <GateFragment />,
    },
    {
      title: "Every suggestion is explained",
      body: "Candidates show the ✓ and ✗ behind their score. Anomalies come with the daily breakdown. Answers cite the policy paragraph they come from.",
      visual: <CandidateFragment />,
    },
    {
      title: "A person approves",
      body: "Nothing moves to the next phase until someone finalizes it. Every AI step and every decision is in the audit trail.",
      visual: <AiDraftFragment />,
    },
  ];
  return (
    <section className="py-24 sm:py-32" aria-labelledby="principles-title">
      <div className="mx-auto max-w-7xl px-4 sm:px-8">
        <h2 id="principles-title" className="max-w-3xl text-3xl font-semibold tracking-tight sm:text-5xl">
          AI that stays accountable.
        </h2>
        <Reveal className="mt-14 grid gap-12 md:grid-cols-3 md:gap-8">
          {items.map((item) => (
            <article key={item.title} className="grid content-start gap-6">
              <div className="rounded-3xl bg-muted/60 p-6">{item.visual}</div>
              <div>
                <h3 className="text-xl font-semibold tracking-tight">{item.title}</h3>
                <p className="mt-2 leading-relaxed text-muted-foreground">{item.body}</p>
              </div>
            </article>
          ))}
        </Reveal>
      </div>
    </section>
  );
}

export function Security() {
  const points = [
    { icon: Lock, title: "Tenant and supplier isolation", body: "Every query is scoped in the backend: a client sees only its missions, a supplier only its own proposals, placements and rates. Out-of-scope ids answer 404." },
    { icon: Eye, title: "PII masked before any model call", body: "Names, emails and phone numbers are replaced with tokens before text leaves the platform, and restored in the answer." },
    { icon: ScrollText, title: "Complete audit trail", body: "Each AI call is logged with model, tokens, latency and cost; each human decision with who and when." },
    { icon: ShieldCheck, title: "GDPR and EU AI Act readiness", body: "Recruitment AI is high-risk under the AI Act: human oversight at every gate, transparency on AI outputs, and logging by design." },
  ];
  return (
    <section id="security" className="border-y bg-card/40 py-24 sm:py-32" aria-labelledby="security-title">
      <div className="mx-auto grid max-w-7xl gap-14 px-4 sm:px-8 lg:grid-cols-[1fr_1fr]">
        <div>
          <h2 id="security-title" className="text-3xl font-semibold tracking-tight sm:text-5xl">
            Security and compliance, by construction.
          </h2>
          <ul className="mt-10 grid gap-7">
            {points.map((p) => (
              <li key={p.title} className="flex gap-4">
                <p.icon className="mt-1 size-5 shrink-0 text-cobalt" aria-hidden />
                <div>
                  <h3 className="font-medium">{p.title}</h3>
                  <p className="mt-1 leading-relaxed text-muted-foreground">{p.body}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>
        <Reveal className="self-center">
          <DataFlowDiagram />
        </Reveal>
      </div>
    </section>
  );
}

function DataFlowDiagram() {
  return (
    <figure className="rounded-3xl border bg-card p-6">
      <svg viewBox="0 0 420 300" className="w-full" role="img" aria-labelledby="flow-title flow-desc">
        <title id="flow-title">How data reaches the AI provider</title>
        <desc id="flow-desc">
          Tenant data passes through scoped queries and PII masking in the AI gateway before reaching the model; answers are unmasked and
          every call is written to the audit log.
        </desc>
        <defs>
          <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
            <path d="M0 0 L10 5 L0 10 z" fill="var(--muted-foreground)" />
          </marker>
        </defs>
        <g fontSize="12" fill="var(--foreground)" fontFamily="inherit">
          <rect x="10" y="40" width="120" height="60" rx="12" fill="var(--muted)" stroke="var(--border)" />
          <text x="70" y="66" textAnchor="middle" fontWeight="600">Tenant data</text>
          <text x="70" y="84" textAnchor="middle" fill="var(--muted-foreground)" fontSize="10">scoped by tenant</text>

          <rect x="150" y="30" width="120" height="80" rx="12" fill="var(--cobalt-soft)" stroke="var(--cobalt)" />
          <text x="210" y="60" textAnchor="middle" fontWeight="600">AI gateway</text>
          <text x="210" y="78" textAnchor="middle" fill="var(--muted-foreground)" fontSize="10">PII → [PERSON_1]</text>
          <text x="210" y="93" textAnchor="middle" fill="var(--muted-foreground)" fontSize="10">budget · rate limit</text>

          <rect x="290" y="40" width="120" height="60" rx="12" fill="var(--muted)" stroke="var(--border)" />
          <text x="350" y="66" textAnchor="middle" fontWeight="600">LLM provider</text>
          <text x="350" y="84" textAnchor="middle" fill="var(--muted-foreground)" fontSize="10">sees masked text only</text>

          <rect x="150" y="190" width="120" height="60" rx="12" fill="var(--muted)" stroke="var(--border)" />
          <text x="210" y="216" textAnchor="middle" fontWeight="600">Audit log</text>
          <text x="210" y="234" textAnchor="middle" fill="var(--muted-foreground)" fontSize="10">every call & decision</text>

          <rect x="10" y="190" width="120" height="60" rx="12" fill="var(--ok-soft)" stroke="var(--ok)" />
          <text x="70" y="216" textAnchor="middle" fontWeight="600">Person approves</text>
          <text x="70" y="234" textAnchor="middle" fill="var(--muted-foreground)" fontSize="10">at every phase gate</text>
        </g>
        <g stroke="var(--muted-foreground)" strokeWidth="1.5" fill="none" markerEnd="url(#arrow)">
          <path d="M130 70 H148" />
          <path d="M270 62 H288" />
          <path d="M290 82 H272" />
          <path d="M210 110 V188" strokeDasharray="4 4" />
          <path d="M150 100 Q 90 140 70 188" />
        </g>
      </svg>
      <figcaption className="mt-2 text-sm text-muted-foreground">Masked on the way out, restored on the way back, logged every time.</figcaption>
    </figure>
  );
}

function CountUp({ to, suffix = "", decimals = 0 }: { to: number; suffix?: string; decimals?: number }) {
  const ref = useRef<HTMLSpanElement>(null);
  const inView = useInView(ref, { once: true, margin: "-60px" });
  const reduced = useReducedMotion();
  const value = useMotionValue(0);
  const text = useTransform(value, (v) => `${v.toFixed(decimals)}${suffix}`);
  useEffect(() => {
    if (!inView) return;
    if (reduced) {
      value.set(to);
      return;
    }
    const controls = animate(value, to, { duration: 1.4, ease: [0.22, 1, 0.36, 1] });
    return () => controls.stop();
  }, [inView, reduced, to, value]);
  return (
    <m.span ref={ref} className="tabular-nums">
      {text}
    </m.span>
  );
}

export function Metrics() {
  const metrics = [
    { value: 6, label: "gated phases per mission" },
    { value: 0, label: "double bookings — enforced by the database", suffix: "" },
    { value: 100, suffix: "%", label: "of AI calls masked and logged" },
    { value: 39.6, suffix: " €", decimals: 2, label: "overbilling caught on a single demo invoice" },
  ];
  return (
    <section className="bg-[#2f4bd6] py-20 text-white sm:py-24" aria-labelledby="metrics-title">
      <div className="mx-auto max-w-7xl px-4 sm:px-8">
        <h2 id="metrics-title" className="sr-only">Figures</h2>
        <dl className="grid gap-10 sm:grid-cols-2 lg:grid-cols-4">
          {metrics.map((m) => (
            <div key={m.label} className="flex flex-col">
              <dt className="order-2 mt-2 text-sm text-white/85">{m.label}</dt>
              <dd className="text-5xl font-semibold tracking-tight">
                <CountUp to={m.value} suffix={m.suffix} decimals={m.decimals} />
              </dd>
            </div>
          ))}
        </dl>
        <p className="mt-10 text-xs text-white/85">Demo figures from the synthetic dataset.</p>
      </div>
    </section>
  );
}

const QUOTES = [
  { quote: "I finally know which mission is waiting on me, and why. The gate tells me what's missing.", name: "Hélène R.", role: "HR manager, logistics" },
  { quote: "Seeing ‘already placed on ORD-…’ before I select someone saved us an awkward Monday.", name: "Bruno T.", role: "Site manager, warehouse" },
  { quote: "The orders arrive complete. No more three emails to get the legal reason and the rate.", name: "Samira K.", role: "Agency account manager" },
  { quote: "The three-way match catches the extra hours before payment, with the credit-note email already written.", name: "Olivier P.", role: "Finance controller" },
  { quote: "The AI explains the anomaly, but I decide. That's the right split for us.", name: "Nadège L.", role: "Plant HR lead, manufacturing" },
];

export function Testimonials() {
  const loop = [...QUOTES, ...QUOTES];
  return (
    <section className="overflow-hidden py-24 sm:py-32" aria-labelledby="quotes-title">
      <div className="mx-auto max-w-7xl px-4 sm:px-8">
        <h2 id="quotes-title" className="text-3xl font-semibold tracking-tight sm:text-5xl">
          Built for everyone on the mission.
        </h2>
        <p className="mt-3 text-sm text-muted-foreground">Illustrative testimonials from fictional personas.</p>
      </div>
      <div className="group mt-12 [mask-image:linear-gradient(to_right,transparent,black_8%,black_92%,transparent)]">
        <ul className="flex w-max gap-5 px-4 motion-safe:animate-[marquee_48s_linear_infinite] group-hover:[animation-play-state:paused] group-focus-within:[animation-play-state:paused]">
          {loop.map((q, i) => (
            <li key={i} aria-hidden={i >= QUOTES.length} className="w-[320px] shrink-0 rounded-3xl border bg-card p-6 sm:w-[380px]">
              <blockquote className="text-lg leading-snug tracking-tight">“{q.quote}”</blockquote>
              <p className="mt-5 text-sm">
                <span className="font-medium">{q.name}</span> <span className="text-muted-foreground">· {q.role}</span>
              </p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

const FAQ = [
  {
    q: "What data does the AI see?",
    a: "Only the text needed for one task (an email, a timesheet breakdown, invoice text), after names, emails and phone numbers are replaced with tokens. The provider never sees other tenants' data.",
  },
  {
    q: "Can the AI reject a candidate?",
    a: "No. Candidates are excluded only by deterministic rules (missing certificate, availability, an overlapping placement), each shown with its reason. Selection is always a person's decision.",
  },
  {
    q: "How is double booking prevented?",
    a: "A worker's placements are stored as date ranges with a PostgreSQL exclusion constraint, so two overlapping placements cannot both be saved — even if two people click at the same time.",
  },
  {
    q: "How are tenants isolated?",
    a: "Every query is scoped by tenant in the backend, and supplier users additionally by supplier. Document search filters by tenant in the same SQL query as the vector search.",
  },
  {
    q: "Which models are used, and can they be swapped?",
    a: "A small, cost-efficient OpenAI chat model and text-embedding-3-small, configured by name. All calls go through one gateway, so another provider can be plugged in without touching the workflow.",
  },
  {
    q: "How is AI quality measured?",
    a: "Golden datasets for extraction, invoices, retrieval and tool use run as an eval suite in CI. A score below its threshold fails the build, and results appear on the admin dashboard.",
  },
  {
    q: "Is it GDPR-friendly?",
    a: "Synthetic data only in the demo. The design minimizes personal data sent to providers, logs every processing step, and keeps a person accountable for every decision.",
  },
  {
    q: "How do I try the demo?",
    a: "Click “Try the live demo”, pick a persona (buyer, supplier or admin) and run a mission from the first email to the closed invoice. The data resets every day.",
  },
];

export function Faq() {
  return (
    <section id="faq" className="border-t py-24 sm:py-32" aria-labelledby="faq-title">
      <div className="mx-auto grid max-w-7xl gap-12 px-4 sm:px-8 lg:grid-cols-[0.8fr_1.2fr]">
        <h2 id="faq-title" className="text-3xl font-semibold tracking-tight sm:text-5xl">
          Questions, answered.
        </h2>
        <Accordion type="single" collapsible className="w-full" data-testid="faq">
          {FAQ.map((f, i) => (
            <AccordionItem key={f.q} value={`q${i}`}>
              <AccordionTrigger className="text-left text-base">{f.q}</AccordionTrigger>
              <AccordionContent className="text-base leading-relaxed text-muted-foreground">{f.a}</AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
      </div>
    </section>
  );
}

export function FinalCta() {
  return (
    <section className="px-4 pb-24 sm:px-8 sm:pb-32">
      <div className="mx-auto max-w-7xl rounded-[2rem] bg-foreground px-6 py-16 text-background sm:px-14 sm:py-24">
        <h2 className="max-w-3xl text-4xl font-semibold leading-[1.05] tracking-tight sm:text-6xl">Run a mission end to end in three minutes.</h2>
        <div className="mt-10 flex flex-wrap items-center gap-5">
          <Button asChild size="lg" className="h-11 bg-background px-5 text-[15px] text-foreground hover:bg-background/90">
            <Link href="/login">
              Try the live demo <ArrowRight aria-hidden />
            </Link>
          </Button>
          <p className="max-w-sm text-sm text-background/70">The demo resets daily and uses synthetic data only. No sign-up.</p>
        </div>
        <ul className="mt-12 flex flex-wrap gap-x-8 gap-y-3 text-sm text-background/70">
          <li className="flex items-center gap-2"><Bot className="size-4" aria-hidden /> AI drafts</li>
          <li className="flex items-center gap-2"><ShieldCheck className="size-4" aria-hidden /> Rules decide</li>
          <li className="flex items-center gap-2"><UserCheck className="size-4" aria-hidden /> You approve</li>
        </ul>
      </div>
    </section>
  );
}
