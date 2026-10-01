import { Footer } from "@/components/landing/footer";
import { Hero } from "@/components/landing/hero";
import { HowItWorks } from "@/components/landing/how-it-works";
import { MotionProvider } from "@/components/landing/motion-provider";
import { Navbar } from "@/components/landing/navbar";
import { ProblemStrip } from "@/components/landing/problem-strip";
import { Faq, FinalCta, Metrics, Principles, Security, Testimonials } from "@/components/landing/sections";

export default function LandingPage() {
  return (
    <MotionProvider>
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[60] focus:rounded-md focus:bg-card focus:px-3 focus:py-2">
        Skip to content
      </a>
      <Navbar />
      <main id="main">
        <Hero />
        <ProblemStrip />
        <HowItWorks />
        <Principles />
        <Security />
        <Metrics />
        <Testimonials />
        <Faq />
        <FinalCta />
      </main>
      <Footer />
    </MotionProvider>
  );
}
