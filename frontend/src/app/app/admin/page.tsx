"use client";

import { AdminDashboard } from "@/components/admin/admin-dashboard";
import { Skeleton } from "@/components/ui/skeleton";
import { useMe } from "@/lib/session";

export default function AdminPage() {
  const me = useMe();
  if (!me.data) return <Skeleton className="h-64 rounded-xl" />;
  if (me.data.role !== "ADMIN") {
    return (
      <p role="alert" className="rounded-xl border bg-card p-6 text-sm text-muted-foreground">
        The AI monitoring dashboard is only available to the admin persona.
      </p>
    );
  }
  return <AdminDashboard />;
}
