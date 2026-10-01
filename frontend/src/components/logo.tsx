import Link from "next/link";
import { cn } from "@/lib/utils";

/** Flowpanel wordmark: six segments, one per phase. */
export function Logo({ className, href = "/" }: { className?: string; href?: string }) {
  return (
    <Link href={href} className={cn("inline-flex items-center gap-2 font-semibold tracking-tight", className)}>
      <svg viewBox="0 0 28 28" className="size-6" aria-hidden>
        <rect x="2" y="2" width="24" height="24" rx="7" className="fill-cobalt" />
        {[0, 1, 2, 3, 4, 5].map((i) => (
          <rect key={i} x={6 + i * 2.8} y={18 - i * 1.6} width="1.8" height={4 + i * 1.6} rx="0.9" className="fill-white" opacity={0.55 + i * 0.09} />
        ))}
      </svg>
      <span>Flowpanel</span>
    </Link>
  );
}
