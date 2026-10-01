"use client";

import { UsageDashboard } from "@/components/admin/usage-dashboard";
import { Skeleton } from "@/components/ui/skeleton";
import { useMe } from "@/lib/session";

export default function UsagePage() {
  const me = useMe();
  if (!me.data) return <Skeleton className="h-64 rounded-xl" />;
  if (me.data.role !== "ADMIN") {
    return (
      <p role="alert" className="rounded-xl border bg-card p-6 text-sm text-muted-foreground">
        LLM usage is only available to the admin persona.
      </p>
    );
  }
  return <UsageDashboard />;
}
