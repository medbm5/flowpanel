import type { Metadata } from "next";
import { Suspense } from "react";
import { Logo } from "@/components/logo";
import { ThemeToggle } from "@/components/theme-toggle";
import { PersonaPicker } from "./persona-picker";

export const metadata: Metadata = {
  title: "Sign in to the demo",
  description: "Pick a demo persona to explore Flowpanel. Synthetic data, reset daily.",
};

export default function LoginPage() {
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="flex items-center justify-between px-4 py-4 sm:px-8">
        <Logo />
        <ThemeToggle />
      </header>
      <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col justify-center px-4 pb-16 sm:px-8">
        <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">Choose a demo persona</h1>
        <p className="mt-3 max-w-xl text-muted-foreground">
          Each persona sees only their own organization. Buyers run missions, suppliers see the orders sent to them,
          the admin watches AI quality and cost.
        </p>
        <Suspense>
          <PersonaPicker />
        </Suspense>
        <p className="mt-8 text-sm text-muted-foreground">
          Demo project, not affiliated with Pixid. All people and companies are fictional. Data resets daily.
        </p>
      </main>
    </div>
  );
}
