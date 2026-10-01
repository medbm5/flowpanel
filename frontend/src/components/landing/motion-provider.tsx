"use client";

import { domAnimation, LazyMotion } from "framer-motion";

/** Loads only the DOM animation features the landing page uses (smaller bundle than `motion`). */
export function MotionProvider({ children }: { children: React.ReactNode }) {
  return (
    <LazyMotion features={domAnimation} strict>
      {children}
    </LazyMotion>
  );
}
