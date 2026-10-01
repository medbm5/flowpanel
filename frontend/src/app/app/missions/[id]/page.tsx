"use client";

import { useParams } from "next/navigation";
import { MissionWorkspace } from "@/components/workflow/mission-workspace";

export default function MissionPage() {
  const { id } = useParams<{ id: string }>();
  return <MissionWorkspace key={id} missionId={Number(id)} />;
}
