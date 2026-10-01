"use client";

import { useMe } from "@/lib/session";

export default function BoardPage() {
  const me = useMe();
  return (
    <section>
      <h1 className="text-2xl font-semibold tracking-tight">Missions</h1>
      <p className="mt-2 text-muted-foreground">Signed in as {me.data?.displayName}.</p>
    </section>
  );
}
