"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, call } from "@/lib/api/client";
import type { MissionDetail, MissionSummary } from "@/lib/api/types";

export type BoardFilter = "all" | "open" | "review" | "closed";

export const missionKeys = {
  all: ["missions"] as const,
  board: (filter: BoardFilter) => ["missions", "board", filter] as const,
  detail: (id: number) => ["missions", "detail", id] as const,
};

export function useMissions(filter: BoardFilter) {
  return useQuery({
    queryKey: missionKeys.board(filter),
    queryFn: () => call<MissionSummary[]>(api.GET("/missions", { params: { query: { status: filter } } })),
  });
}

export function useTemplates() {
  return useQuery({
    queryKey: ["templates"],
    queryFn: () => call(api.GET("/request-templates")),
    staleTime: Infinity,
  });
}

/** Summary row for the board, built from the created mission (so the board updates without a refetch). */
export function toSummary(m: MissionDetail): MissionSummary {
  return {
    id: m.id,
    ref: m.ref,
    title: m.title,
    site: m.site,
    phase: m.phase,
    phases: m.phases,
    positions: m.positions,
    nextAction: m.nextAction,
    needsReview: m.needsReview,
    createdAt: m.createdAt,
    updatedAt: m.createdAt,
  };
}

export function useCreateMission() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { templateCode?: string; emailText?: string }) =>
      call<MissionDetail>(api.POST("/missions", { body })),
    onSuccess: (mission) => {
      const row = toSummary(mission);
      for (const filter of ["all", "open"] as const) {
        queryClient.setQueryData<MissionSummary[]>(missionKeys.board(filter), (rows) =>
          rows ? [row, ...rows.filter((r) => r.id !== row.id)] : rows,
        );
      }
      queryClient.setQueryData(missionKeys.detail(mission.id), mission);
      void queryClient.invalidateQueries({ queryKey: missionKeys.all, refetchType: "none" });
    },
  });
}
