"use client";

import { useRouter } from "next/navigation";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useMissions } from "@/lib/missions";

/** "Other missions" switcher: jump between missions without going back to the board. */
export function MissionSwitcher({ currentId }: { currentId: number }) {
  const missions = useMissions("all");
  const router = useRouter();
  if (!missions.data || missions.data.length < 2) return null;
  return (
    <Select value={String(currentId)} onValueChange={(v) => router.push(`/app/missions/${v}`)}>
      <SelectTrigger className="w-full sm:w-72" aria-label="Other missions">
        <SelectValue placeholder="Other missions" />
      </SelectTrigger>
      <SelectContent>
        {missions.data.map((m) => (
          <SelectItem key={m.id} value={String(m.id)}>
            <span className="font-mono text-xs text-muted-foreground">{m.ref}</span> <span className="truncate">{m.title}</span>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
