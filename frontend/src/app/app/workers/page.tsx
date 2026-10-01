"use client";

import { WorkerPoolPage } from "@/components/supplier/worker-pool";
import { Skeleton } from "@/components/ui/skeleton";
import { useMe } from "@/lib/session";

export default function WorkersPage() {
  const me = useMe();
  if (!me.data) return <Skeleton className="h-64 rounded-xl" />;
  if (me.data.role !== "SUPPLIER") {
    return <p className="rounded-xl border bg-card p-6 text-sm text-muted-foreground">This page is for staffing agencies.</p>;
  }
  return <WorkerPoolPage />;
}
