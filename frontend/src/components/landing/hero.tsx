"use client";

import { m, type MotionValue, useMotionValue, useReducedMotion, useScroll, useSpring, useTransform } from "framer-motion";
import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { useRef } from "react";
import { Button } from "@/components/ui/button";
import { CandidateFragment, GateFragment, MatchRowFragment, MissionRowFragment } from "./fragments";
import { useMedia } from "./use-media";

/** Each layer moves at its own speed on scroll and slightly with the pointer; static below 768px or with reduced motion. */
function Layer({
  children,
  className,
  scrollY,
  pointerX,
  pointerY,
  speed,
  depth,
  enabled,
}: {
  children: React.ReactNode;
  className: string;
  scrollY: MotionValue<number>;
  pointerX: MotionValue<number>;
  pointerY: MotionValue<number>;
  speed: number;
  depth: number;
  enabled: boolean;
}) {
  const y = useTransform(scrollY, [0, 600], [0, -600 * speed]);
  const px = useTransform(pointerX, (v) => v * depth);
  const py = useTransform(pointerY, (v) => v * depth);
  const translateY = useTransform([y, py], ([a, b]: number[]) => a + b);
  return (
    <m.div className={className} style={enabled ? { y: translateY, x: px } : undefined}>
      {children}
    </m.div>
  );
}

export function Hero() {
  const ref = useRef<HTMLDivElement>(null);
  const reduced = useReducedMotion();
  const wide = useMedia("(min-width: 768px)");
  const enabled = wide && !reduced;
  const { scrollY } = useScroll();
  const rawX = useMotionValue(0);
  const rawY = useMotionValue(0);
  const pointerX = useSpring(rawX, { stiffness: 80, damping: 20 });
  const pointerY = useSpring(rawY, { stiffness: 80, damping: 20 });

  function onPointerMove(event: React.PointerEvent) {
    if (!enabled || !ref.current) return;
    const rect = ref.current.getBoundingClientRect();
    rawX.set((event.clientX - rect.left) / rect.width - 0.5);
    rawY.set((event.clientY - rect.top) / rect.height - 0.5);
  }

  const common = { scrollY, pointerX, pointerY, enabled };

  return (
    <section id="product" className="relative overflow-hidden pt-28 pb-20 sm:pt-36 md:pb-32" onPointerMove={onPointerMove}>
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 -z-10 [background-image:linear-gradient(to_right,var(--border)_1px,transparent_1px),linear-gradient(to_bottom,var(--border)_1px,transparent_1px)] [background-size:56px_56px] [mask-image:radial-gradient(ellipse_70%_60%_at_60%_30%,black,transparent)] opacity-60"
      />
      <div className="mx-auto grid max-w-7xl items-center gap-14 px-4 sm:px-8 lg:grid-cols-[1.05fr_1fr]">
        <div>
          <p className="text-sm font-medium text-cobalt">Vendor management for temporary staffing</p>
          <h1 className="mt-5 text-[2.6rem] font-semibold leading-[1.02] tracking-[-0.03em] sm:text-6xl lg:text-7xl">
            From the first email to the last invoice, one workflow.
          </h1>
          <p className="mt-6 max-w-xl text-lg leading-relaxed text-muted-foreground">
            Flowpanel takes a staffing request from a site manager&apos;s email to a paid, matched invoice — through six gated phases. The
            AI reads, ranks and drafts. Rules decide. A person approves.
          </p>
          <div className="mt-9 flex flex-wrap gap-3">
            <Button asChild size="lg" className="h-11 px-5 text-[15px]" data-testid="hero-cta">
              <Link href="/login">
                Try the live demo <ArrowRight aria-hidden />
              </Link>
            </Button>
            <Button asChild size="lg" variant="outline" className="h-11 px-5 text-[15px]">
              <a href="#how">See how it works</a>
            </Button>
          </div>
          <p className="mt-5 text-sm text-muted-foreground">No sign-up. Synthetic data, reset daily.</p>
        </div>

        <div ref={ref} className="relative grid gap-4 md:block md:h-[520px]" aria-label="Flowpanel interface preview" role="img">
          <Layer {...common} speed={0.08} depth={10} className="md:absolute md:left-[4%] md:top-[2%] md:w-[62%]">
            <MissionRowFragment />
          </Layer>
          <Layer {...common} speed={0.22} depth={22} className="md:absolute md:right-0 md:top-[22%] md:w-[48%]">
            <GateFragment />
          </Layer>
          <Layer {...common} speed={0.14} depth={16} className="md:absolute md:left-0 md:top-[42%] md:w-[54%]">
            <CandidateFragment />
          </Layer>
          <Layer {...common} speed={0.3} depth={28} className="md:absolute md:bottom-[2%] md:right-[6%] md:w-[52%]">
            <MatchRowFragment />
          </Layer>
        </div>
      </div>
    </section>
  );
}
