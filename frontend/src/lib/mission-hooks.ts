"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { api, call, errorMessage } from "@/lib/api/client";
import type {
  AuditView,
  ContractsView,
  IntakeView,
  InvoicesView,
  MissionDetail,
  Phase,
  SourcingView,
  TimesheetsView,
} from "@/lib/api/types";
import { missionKeys } from "@/lib/missions";

const path = (id: number) => ({ params: { path: { id } } });

export const phaseKeys = {
  intake: (id: number) => ["missions", id, "intake"] as const,
  sourcing: (id: number) => ["missions", id, "sourcing"] as const,
  contracts: (id: number) => ["missions", id, "contracts"] as const,
  timesheets: (id: number) => ["missions", id, "timesheets"] as const,
  invoice: (id: number) => ["missions", id, "invoice"] as const,
  audit: (id: number) => ["missions", id, "audit"] as const,
};

export function useMission(id: number) {
  return useQuery({ queryKey: missionKeys.detail(id), queryFn: () => call<MissionDetail>(api.GET("/missions/{id}", path(id))) });
}

export function useAudit(id: number) {
  return useQuery({ queryKey: phaseKeys.audit(id), queryFn: () => call<AuditView[]>(api.GET("/missions/{id}/audit", path(id))) });
}

export function useIntake(id: number) {
  return useQuery({ queryKey: phaseKeys.intake(id), queryFn: () => call<IntakeView>(api.GET("/missions/{id}/intake", path(id))) });
}

export function useSourcing(id: number) {
  return useQuery({ queryKey: phaseKeys.sourcing(id), queryFn: () => call<SourcingView>(api.GET("/missions/{id}/sourcing", path(id))) });
}

export function useContracts(id: number) {
  return useQuery({ queryKey: phaseKeys.contracts(id), queryFn: () => call<ContractsView>(api.GET("/missions/{id}/contracts", path(id))) });
}

export function useTimesheets(id: number) {
  return useQuery({
    queryKey: phaseKeys.timesheets(id),
    queryFn: () => call<TimesheetsView>(api.GET("/missions/{id}/timesheets", path(id))),
  });
}

export function useInvoices(id: number) {
  return useQuery({ queryKey: phaseKeys.invoice(id), queryFn: () => call<InvoicesView>(api.GET("/missions/{id}/invoice", path(id))) });
}

/**
 * Runs a phase action, writes the returned phase view into the cache, refreshes the mission (gate, artifacts) and the
 * audit trail, and shows the ProblemDetail message on failure.
 */
export function usePhaseAction<TVars, TResult>(
  missionId: number,
  key: readonly unknown[] | null,
  fn: (vars: TVars) => Promise<TResult>,
  successMessage?: (result: TResult, vars: TVars) => string | null,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (result, vars) => {
      if (key) queryClient.setQueryData(key, result);
      void queryClient.invalidateQueries({ queryKey: missionKeys.detail(missionId) });
      void queryClient.invalidateQueries({ queryKey: phaseKeys.audit(missionId) });
      void queryClient.invalidateQueries({ queryKey: ["missions", "board"] });
      const message = successMessage?.(result, vars);
      if (message) toast.success(message);
    },
    onError: (error) => toast.error("Action refused", { description: errorMessage(error) }),
  });
}

export function useFinalize(missionId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (phase: Phase) =>
      call<MissionDetail>(api.POST("/missions/{id}/phases/{phase}/finalize", { params: { path: { id: missionId, phase } } })),
    onSuccess: (mission, phase) => {
      queryClient.setQueryData(missionKeys.detail(missionId), mission);
      void queryClient.invalidateQueries({ queryKey: ["missions", missionId] });
      void queryClient.invalidateQueries({ queryKey: ["missions", "board"] });
      toast.success(`${phase.charAt(0)}${phase.slice(1).toLowerCase()} finalized`, {
        description: mission.closed ? "The mission is closed." : `Next: ${mission.nextAction}`,
      });
    },
    onError: (error) => toast.error("Cannot finalize yet", { description: errorMessage(error) }),
  });
}
