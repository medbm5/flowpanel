"use client";

import { LogOut } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { CopilotSheet } from "@/components/copilot/copilot-sheet";
import { Logo } from "@/components/logo";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/lib/api/client";
import { useLogout, useMe } from "@/lib/session";

/** Top bar of the product app: organization, user, theme toggle, logout. */
export function AppShell({ children, actions }: { children: React.ReactNode; actions?: React.ReactNode }) {
  const me = useMe();
  const logout = useLogout();
  const router = useRouter();
  const pathname = usePathname();
  const expired = me.error instanceof ApiError && me.error.status === 401;

  useEffect(() => {
    if (expired) {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    }
  }, [expired, pathname, router]);
  const organization = me.data?.tenant?.name ?? me.data?.supplier?.name ?? (me.data?.role === "ADMIN" ? "Platform" : "");

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="sticky top-0 z-30 border-b bg-background/85 backdrop-blur supports-[backdrop-filter]:bg-background/70">
        <div className="mx-auto flex h-14 w-full max-w-7xl items-center gap-3 px-4 sm:px-6">
          <Logo href="/app" />
          <span className="hidden h-5 w-px bg-border sm:block" aria-hidden />
          {me.isLoading ? (
            <Skeleton className="h-5 w-28" />
          ) : (
            <span className="truncate text-sm font-medium" data-testid="tenant-name">
              {organization}
            </span>
          )}
          <nav className="ml-2 hidden items-center gap-1 text-sm md:flex" aria-label="Main">
            {me.data?.role !== "ADMIN" && (
              <Link className="rounded-md px-2 py-1 text-muted-foreground hover:bg-muted hover:text-foreground" href="/app">
                {me.data?.role === "SUPPLIER" ? "Orders" : "Missions"}
              </Link>
            )}
            {me.data?.role === "ADMIN" && (
              <Link className="rounded-md px-2 py-1 text-muted-foreground hover:bg-muted hover:text-foreground" href="/app/admin">
                AI monitoring
              </Link>
            )}
            {me.data?.role === "ADMIN" && (
              <Link className="rounded-md px-2 py-1 text-muted-foreground hover:bg-muted hover:text-foreground" href="/app/admin/usage">
                LLM usage
              </Link>
            )}
          </nav>
          <div className="ml-auto flex items-center gap-1">
            {me.data && me.data.role !== "ADMIN" && <CopilotSheet />}
            {actions}
            <span className="hidden text-right text-sm leading-tight sm:block">
              <span className="block font-medium">{me.data?.displayName}</span>
              <span className="block text-xs text-muted-foreground">{me.data?.jobTitle}</span>
            </span>
            <ThemeToggle />
            <Button variant="ghost" size="icon" aria-label="Sign out" onClick={() => logout.mutate()}>
              <LogOut aria-hidden />
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6">{children}</main>
    </div>
  );
}
