import type { MissionDetail, MissionSummary, PhaseStep } from "@/lib/api/types";
import { PHASE_LABEL, PHASES } from "@/lib/format";

export function phases(activeIndex: number): PhaseStep[] {
  return PHASES.map((phase, i) => ({
    phase,
    label: PHASE_LABEL[phase],
    state: i < activeIndex ? "DONE" : i === activeIndex ? "ACTIVE" : "LOCKED",
    finalizedAt: i < activeIndex ? "2026-09-20T10:00:00Z" : null,
    finalizedBy: i < activeIndex ? "Claire Dubois" : null,
  })) as PhaseStep[];
}

export function summary(overrides: Partial<MissionSummary> = {}): MissionSummary {
  return {
    id: 1,
    ref: "ORD-2026-0142",
    title: "Caristes CACES 3 — inventaire Lesquin",
    site: "Entrepôt Lille Lesquin",
    phase: "TIMESHEETS",
    phases: phases(3),
    positions: { filled: 2, total: 2 },
    nextAction: "Run the timesheet checks",
    needsReview: false,
    createdAt: "2026-09-15T08:00:00Z",
    updatedAt: "2026-09-25T08:00:00Z",
    ...overrides,
  };
}

export function detail(overrides: Partial<MissionDetail> = {}): MissionDetail {
  return {
    id: 99,
    ref: "ORD-2026-0150",
    title: "Caristes CACES 3 — Lille Lesquin",
    site: "Entrepôt Lille Lesquin",
    phase: "INTAKE",
    closed: false,
    needsReview: false,
    nextAction: "Extract the order from the email",
    gate: { phase: "INTAKE", checks: [], ready: false },
    phases: phases(0),
    artifacts: [],
    positions: { filled: 0, total: null as unknown as number },
    sourceEmail: "Objet : test",
    templateCode: "forklift-lille",
    createdAt: "2026-10-01T08:00:00Z",
    closedAt: null as unknown as string,
    summary: null as unknown as Record<string, never>,
    ...overrides,
  };
}

/** Minimal fetch stub: routes "METHOD /path" to JSON responses. */
export function stubFetch(routes: Record<string, (body?: unknown) => unknown>) {
  const calls: string[] = [];
  globalThis.fetch = (async (input: RequestInfo | URL) => {
    const request = input instanceof Request ? input : new Request(String(input));
    const url = new URL(request.url);
    const key = `${request.method} ${url.pathname.replace(/^\/api/, "")}`;
    calls.push(`${key}${url.search}`);
    const handler = routes[key];
    if (!handler) {
      return new Response(JSON.stringify({ title: "Not found", detail: key }), { status: 404 });
    }
    const body = request.method === "GET" ? undefined : await request.clone().json().catch(() => undefined);
    return new Response(JSON.stringify(handler(body)), { status: 200, headers: { "Content-Type": "application/json" } });
  }) as typeof fetch;
  return calls;
}
