"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, RotateCcw } from "lucide-react";
import { toast } from "sonner";
import { useMutation } from "@tanstack/react-query";
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { api, call, errorMessage } from "@/lib/api/client";
import type { EvalRun, MetricsOverview } from "@/lib/api/types";
import { dateTime, percent } from "@/lib/format";
import { AuditExplorer } from "./audit-explorer";

const AXIS = { fontSize: 11, fill: "var(--muted-foreground)" };
const TOOLTIP = {
  contentStyle: { background: "var(--popover)", border: "1px solid var(--border)", borderRadius: 8, fontSize: 12, color: "var(--foreground)" },
  cursor: { fill: "var(--muted)", opacity: 0.5 },
};
const EVAL_SERIES = [
  { key: "intake.field_accuracy", label: "Intake field accuracy", color: "var(--chart-1)" },
  { key: "invoice.line_accuracy", label: "Invoice line accuracy", color: "var(--chart-2)" },
  { key: "rag.hit_rate", label: "Retrieval hit rate", color: "var(--chart-3)" },
  { key: "tools.choice_accuracy", label: "Tool choice accuracy", color: "var(--chart-4)" },
];

export function AdminDashboard() {
  const overview = useQuery({ queryKey: ["admin", "overview"], queryFn: () => call<MetricsOverview>(api.GET("/admin/metrics/overview")) });
  const evals = useQuery({
    queryKey: ["admin", "evals"],
    queryFn: () => call<EvalRun[]>(api.GET("/admin/metrics/evals", { params: { query: { limit: 30 } } })),
  });
  const queryClient = useQueryClient();
  const reset = useMutation({
    mutationFn: () => call(api.POST("/admin/demo/reset")),
    onSuccess: () => {
      toast.success("Demo data reset");
      void queryClient.invalidateQueries();
    },
    onError: (e) => toast.error("Reset failed", { description: errorMessage(e) }),
  });

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">AI monitoring</h1>
          <p className="text-sm text-muted-foreground">Latency, tokens, estimated cost, errors and quality of every AI call, across tenants.</p>
        </div>
        <Button variant="outline" onClick={() => reset.mutate()} disabled={reset.isPending}>
          {reset.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <RotateCcw aria-hidden />} Reset demo data
        </Button>
      </div>

      {overview.isLoading && <Skeleton className="h-64 rounded-xl" />}
      {overview.isError && (
        <p role="alert" className="rounded-xl border border-bad/30 bg-bad-soft p-4 text-sm text-bad">
          {errorMessage(overview.error)}
        </p>
      )}
      {overview.data && <Overview data={overview.data} />}

      <section className="rounded-xl border bg-card p-4" aria-labelledby="evals-title">
        <h2 id="evals-title" className="text-sm font-semibold">Eval scores over time</h2>
        <p className="text-xs text-muted-foreground">Golden datasets run in CI; a score under its threshold fails the build.</p>
        {evals.data && evals.data.length === 0 && (
          <p className="mt-4 rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
            No eval run yet. Run <code className="font-mono">make eval</code> to post one.
          </p>
        )}
        {evals.data && evals.data.length > 0 && <EvalChart runs={evals.data} />}
      </section>

      <AuditExplorer />
    </div>
  );
}

function Overview({ data }: { data: MetricsOverview }) {
  const s = data.summary;
  const tiles = [
    { label: "AI calls", value: s.calls.toLocaleString("en-GB") },
    { label: "Latency p50 / p95", value: `${s.p50LatencyMs ?? "—"} / ${s.p95LatencyMs ?? "—"} ms` },
    { label: "Error rate", value: percent(s.errorRate), hint: `${s.errors} invalid or failed` },
    { label: "Tokens", value: s.tokens.toLocaleString("en-GB") },
    { label: "Estimated cost", value: `$${Number(s.estimatedCostUsd).toFixed(4)}`, hint: s.profile === "mock" ? "simulated (mock profile)" : "live" },
    {
      label: "Live spend today",
      value: `$${Number(s.liveSpendTodayUsd).toFixed(4)}`,
      hint: `budget $${Number(s.dailyBudgetUsd).toFixed(2)}/day`,
    },
    {
      label: "AI fields corrected",
      value: percent(data.corrections.share),
      hint: `${data.corrections.correctedFields} of ${data.corrections.aiFields} extracted fields`,
    },
  ];
  const features = data.byFeature.map((f) => ({ ...f, p50: f.p50LatencyMs ?? 0, p95: f.p95LatencyMs ?? 0, cost: Number(f.estimatedCostUsd) }));
  const tenants = data.byTenant.map((t) => ({ ...t, cost: Number(t.estimatedCostUsd) }));

  return (
    <>
      <dl className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-7" data-testid="metric-tiles">
        {tiles.map((t) => (
          <div key={t.label} className="rounded-xl border bg-card p-3">
            <dt className="text-xs text-muted-foreground">{t.label}</dt>
            <dd className="mt-1 text-lg font-semibold tabular-nums">{t.value}</dd>
            {t.hint && <dd className="text-[11px] text-muted-foreground">{t.hint}</dd>}
          </div>
        ))}
      </dl>

      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard title="Latency by feature (ms)" subtitle="p50 and p95 of successful calls">
          <ResponsiveContainer width="100%" height={Math.max(220, features.length * 34)}>
            <BarChart data={features} layout="vertical" margin={{ left: 8, right: 16 }} barGap={2}>
              <CartesianGrid horizontal={false} stroke="var(--border)" strokeDasharray="2 4" />
              <XAxis type="number" tick={AXIS} axisLine={false} tickLine={false} />
              <YAxis type="category" dataKey="feature" width={130} tick={AXIS} axisLine={false} tickLine={false} />
              <Tooltip {...TOOLTIP} />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              <Bar dataKey="p50" name="p50" fill="var(--chart-1)" radius={[0, 4, 4, 0]} barSize={8} />
              <Bar dataKey="p95" name="p95" fill="var(--chart-2)" radius={[0, 4, 4, 0]} barSize={8} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
        <ChartCard title="Estimated cost per tenant (USD)" subtitle="Sum of ai_call estimated cost">
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={tenants} layout="vertical" margin={{ left: 8, right: 16 }}>
              <CartesianGrid horizontal={false} stroke="var(--border)" strokeDasharray="2 4" />
              <XAxis type="number" tick={AXIS} axisLine={false} tickLine={false} tickFormatter={(v: number) => `$${v.toFixed(3)}`} />
              <YAxis type="category" dataKey="tenant" width={130} tick={AXIS} axisLine={false} tickLine={false} />
              <Tooltip {...TOOLTIP} formatter={(v) => `$${Number(v).toFixed(5)}`} />
              <Bar dataKey="cost" name="Cost" fill="var(--chart-1)" radius={[0, 4, 4, 0]} barSize={12} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
      </div>

      <section className="overflow-x-auto rounded-xl border bg-card" aria-labelledby="features-title">
        <h2 id="features-title" className="px-4 pt-4 text-sm font-semibold">Per feature</h2>
        <table className="mt-2 w-full min-w-[640px] text-sm">
          <thead className="text-xs text-muted-foreground">
            <tr className="border-b">
              <th scope="col" className="px-4 py-2 text-left font-medium">Feature</th>
              <th scope="col" className="px-3 py-2 text-right font-medium">Calls</th>
              <th scope="col" className="px-3 py-2 text-right font-medium">Error rate</th>
              <th scope="col" className="px-3 py-2 text-right font-medium">p50 / p95 ms</th>
              <th scope="col" className="px-3 py-2 text-right font-medium">Tokens</th>
              <th scope="col" className="px-4 py-2 text-right font-medium">Est. cost</th>
            </tr>
          </thead>
          <tbody>
            {data.byFeature.map((f) => (
              <tr key={f.feature} className="border-b last:border-0">
                <th scope="row" className="px-4 py-2 text-left font-mono text-xs font-normal">{f.feature}</th>
                <td className="px-3 py-2 text-right tabular-nums">{f.calls}</td>
                <td className="px-3 py-2 text-right tabular-nums">{percent(f.errorRate)}</td>
                <td className="px-3 py-2 text-right tabular-nums">
                  {f.p50LatencyMs ?? "—"} / {f.p95LatencyMs ?? "—"}
                </td>
                <td className="px-3 py-2 text-right tabular-nums">{f.tokens.toLocaleString("en-GB")}</td>
                <td className="px-4 py-2 text-right tabular-nums">${Number(f.estimatedCostUsd).toFixed(5)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </>
  );
}

function EvalChart({ runs }: { runs: EvalRun[] }) {
  const data = [...runs].reverse().map((r) => ({
    at: dateTime(r.createdAt),
    sha: r.gitSha.slice(0, 7),
    ...Object.fromEntries(EVAL_SERIES.map((s) => [s.key, typeof r.metrics[s.key] === "number" ? r.metrics[s.key] : null])),
  }));
  return (
    <div className="mt-3">
      <ResponsiveContainer width="100%" height={240}>
        <LineChart data={data} margin={{ left: 0, right: 16, top: 8 }}>
          <CartesianGrid vertical={false} stroke="var(--border)" strokeDasharray="2 4" />
          <XAxis dataKey="sha" tick={AXIS} axisLine={false} tickLine={false} />
          <YAxis domain={[0, 1]} tick={AXIS} axisLine={false} tickLine={false} tickFormatter={(v: number) => `${Math.round(v * 100)}%`} width={40} />
          <Tooltip {...TOOLTIP} cursor={{ stroke: "var(--border)" }} formatter={(v) => `${Math.round(Number(v) * 100)}%`} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          {EVAL_SERIES.map((s) => (
            <Line key={s.key} dataKey={s.key} name={s.label} stroke={s.color} strokeWidth={2} dot={{ r: 4 }} connectNulls />
          ))}
        </LineChart>
      </ResponsiveContainer>
      <p className="mt-2 text-xs text-muted-foreground">
        Latest run: {runs[0].gitSha.slice(0, 7)} · {runs[0].passed ? "passed" : "failed"} · {dateTime(runs[0].createdAt)}
      </p>
    </div>
  );
}

function ChartCard({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  return (
    <section className="rounded-xl border bg-card p-4">
      <h2 className="text-sm font-semibold">{title}</h2>
      <p className="mb-3 text-xs text-muted-foreground">{subtitle}</p>
      {children}
    </section>
  );
}
