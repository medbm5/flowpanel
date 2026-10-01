"use client";

import { useParams } from "next/navigation";

export default function MissionPage() {
  const { id } = useParams<{ id: string }>();
  return <h1 className="text-2xl font-semibold tracking-tight">Mission {id}</h1>;
}
