"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { MissionBoard } from "@/components/mission/mission-board";
import { SupplierBoard } from "@/components/supplier/supplier-board";
import { Skeleton } from "@/components/ui/skeleton";
import { useMe } from "@/lib/session";

export default function BoardPage() {
  const me = useMe();
  const router = useRouter();
  const isAdmin = me.data?.role === "ADMIN";

  useEffect(() => {
    if (isAdmin) router.replace("/app/admin");
  }, [isAdmin, router]);

  if (!me.data || isAdmin) {
    return <Skeleton className="h-40 rounded-xl" />;
  }
  if (me.data.role === "SUPPLIER") {
    return <SupplierBoard supplierName={me.data.supplier?.name ?? "your agency"} />;
  }
  return <MissionBoard />;
}
