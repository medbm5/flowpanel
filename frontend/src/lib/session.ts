"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { api, call } from "@/lib/api/client";
import type { Me } from "@/lib/api/types";

export function useMe() {
  return useQuery({
    queryKey: ["me"],
    queryFn: () => call<Me>(api.GET("/auth/me")),
    staleTime: 60_000,
  });
}

export function usePersonas() {
  return useQuery({
    queryKey: ["personas"],
    queryFn: () => call(api.GET("/auth/personas")),
    staleTime: Infinity,
  });
}

/** Where a persona lands after sign-in. */
export function homeFor(me: Pick<Me, "role">): string {
  return me.role === "ADMIN" ? "/app/admin" : "/app";
}

export function useLogin(next?: string | null) {
  const router = useRouter();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (persona: string) => call<Me>(api.POST("/auth/demo-login", { body: { persona } })),
    onSuccess: (me) => {
      queryClient.clear();
      queryClient.setQueryData(["me"], me);
      router.push(next && next.startsWith("/app") ? next : homeFor(me));
    },
  });
}

export function useLogout() {
  const router = useRouter();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => call(api.POST("/auth/logout")),
    onSettled: () => {
      queryClient.clear();
      router.push("/login");
    },
  });
}
