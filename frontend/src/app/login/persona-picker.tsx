"use client";

import { Building2, Loader2, ShieldCheck, Truck } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { toast } from "sonner";
import { Skeleton } from "@/components/ui/skeleton";
import { errorMessage } from "@/lib/api/client";
import { useLogin, usePersonas } from "@/lib/session";

const ROLE_ICON = { BUYER: Building2, SUPPLIER: Truck, ADMIN: ShieldCheck } as const;
const ROLE_LABEL = { BUYER: "Client · buyer", SUPPLIER: "Staffing supplier", ADMIN: "Platform admin" } as const;

export function PersonaPicker() {
  const next = useSearchParams().get("next");
  const personas = usePersonas();
  const login = useLogin(next);

  if (personas.isLoading) {
    return (
      <div className="mt-8 grid gap-3 sm:grid-cols-2">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-24 rounded-xl" />
        ))}
      </div>
    );
  }
  if (personas.isError) {
    return (
      <p role="alert" className="mt-8 rounded-lg border border-bad/30 bg-bad-soft p-4 text-sm text-bad">
        The demo backend is not reachable. It may be waking up (free hosting) — retry in a minute.
      </p>
    );
  }

  return (
    <ul className="mt-8 grid gap-3 sm:grid-cols-2">
      {personas.data?.map((p) => {
        const Icon = ROLE_ICON[p.role];
        const pending = login.isPending && login.variables === p.persona;
        return (
          <li key={p.persona}>
            <button
              type="button"
              disabled={login.isPending}
              onClick={() =>
                login.mutate(p.persona, { onError: (e) => toast.error("Sign-in failed", { description: errorMessage(e) }) })
              }
              className="group flex w-full items-start gap-3 rounded-xl border bg-card p-4 text-left transition-colors hover:border-cobalt/50 hover:bg-accent/40 disabled:opacity-60"
              data-testid={`persona-${p.persona}`}
            >
              <span className="mt-0.5 grid size-9 shrink-0 place-items-center rounded-lg bg-accent text-accent-foreground">
                {pending ? <Loader2 className="size-4 animate-spin" aria-hidden /> : <Icon className="size-4" aria-hidden />}
              </span>
              <span className="min-w-0">
                <span className="block font-medium">{p.displayName}</span>
                <span className="block text-sm text-muted-foreground">{p.jobTitle}</span>
                <span className="mt-1 block text-xs uppercase tracking-wide text-muted-foreground/80">
                  {ROLE_LABEL[p.role]}
                </span>
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
