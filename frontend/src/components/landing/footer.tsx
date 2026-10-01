import Link from "next/link";
import { Logo } from "@/components/logo";

const GITHUB_URL = process.env.NEXT_PUBLIC_GITHUB_URL ?? "https://github.com";

export function Footer() {
  return (
    <footer className="border-t">
      <div className="mx-auto grid max-w-7xl gap-10 px-4 py-14 sm:px-8 md:grid-cols-[1.5fr_1fr_1fr]">
        <div>
          <Logo />
          <p className="mt-3 max-w-xs text-sm text-muted-foreground">
            Temporary staffing missions from the first email to the last invoice, with AI that stays accountable.
          </p>
        </div>
        <nav aria-label="Product">
          <h2 className="text-sm font-medium">Product</h2>
          <ul className="mt-3 grid gap-2 text-sm text-muted-foreground">
            <li><a href="#how" className="hover:text-foreground">How it works</a></li>
            <li><a href="#security" className="hover:text-foreground">Security</a></li>
            <li><a href="#faq" className="hover:text-foreground">FAQ</a></li>
            <li><Link href="/login" className="hover:text-foreground">Live demo</Link></li>
          </ul>
        </nav>
        <nav aria-label="Project">
          <h2 className="text-sm font-medium">Project</h2>
          <ul className="mt-3 grid gap-2 text-sm text-muted-foreground">
            <li><a href={GITHUB_URL} className="hover:text-foreground" rel="noreferrer" target="_blank">GitHub</a></li>
          </ul>
        </nav>
      </div>
      <div className="mx-auto flex max-w-7xl flex-wrap justify-between gap-2 border-t px-4 py-6 text-xs text-muted-foreground sm:px-8">
        <p>Demo project, not affiliated with Pixid. All people, companies and figures are fictional.</p>
        <p>© {new Date().getFullYear()} Flowpanel demo</p>
      </div>
    </footer>
  );
}
