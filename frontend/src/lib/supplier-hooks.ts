"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { api, call, errorMessage } from "@/lib/api/client";
import type { SupplierDashboard, SupplierWorkspace, WorkerPool } from "@/lib/api/types";

export const supplierKeys = {
  all: ["supplier"] as const,
  dashboard: ["supplier", "dashboard"] as const,
  workspace: (id: number) => ["supplier", "workspace", id] as const,
  workers: ["supplier", "workers"] as const,
};

export function useSupplierDashboard() {
  return useQuery({ queryKey: supplierKeys.dashboard, queryFn: () => call<SupplierDashboard>(api.GET("/supplier/dashboard")) });
}

export function useWorkspace(missionId: number) {
  return useQuery({
    queryKey: supplierKeys.workspace(missionId),
    queryFn: () =>
      call<SupplierWorkspace>(api.GET("/supplier/orders/{missionId}/workspace", { params: { path: { missionId } } })),
  });
}

export function useWorkerPool() {
  return useQuery({ queryKey: supplierKeys.workers, queryFn: () => call<WorkerPool>(api.GET("/supplier/workers")) });
}

/** Runs a supplier action, refreshes the order workspace, dashboard and pool, and reports the outcome. */
export function useSupplierAction<TVars, TResult>(
  missionId: number | null,
  fn: (vars: TVars) => Promise<TResult>,
  success: (result: TResult, vars: TVars) => string,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (result, vars) => {
      if (missionId !== null && result && typeof result === "object" && "proposals" in result) {
        queryClient.setQueryData(supplierKeys.workspace(missionId), result);
      }
      void queryClient.invalidateQueries({ queryKey: supplierKeys.all });
      toast.success(success(result, vars));
    },
    onError: (error) => toast.error("Action refused", { description: errorMessage(error) }),
  });
}
