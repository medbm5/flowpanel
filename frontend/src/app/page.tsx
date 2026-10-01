import Link from "next/link";
import { Logo } from "@/components/logo";
import { Button } from "@/components/ui/button";

/** Placeholder; the marketing landing page is built in Slice 15. */
export default function HomePage() {
  return (
    <main className="mx-auto flex min-h-dvh max-w-3xl flex-col justify-center gap-6 px-4">
      <Logo />
      <h1 className="text-4xl font-semibold tracking-tight">From the first email to the last invoice, one workflow.</h1>
      <Button asChild size="lg" className="w-fit">
        <Link href="/login">Try the live demo</Link>
      </Button>
    </main>
  );
}
