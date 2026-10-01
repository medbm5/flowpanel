"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, ChevronDown, Gauge, KeyRound, ShieldAlert, Users } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { StatusPill } from "@/components/mission/status-badges";
import { Skeleton } from "@/components/ui/skeleton";
import { api, call, errorMessage } from "@/lib/api/client";
import type { UsageBreakdown, UsageOverview, UsageUserDetail, UserUsage } from "@/lib/api/types";
import { dateTime, percent, shortDate } from "@/lib/format";
import { cn } from "@/lib/utils";

const WINDOWS = [
  { days: 1, label: "Today" },
  { days: 7, label: "7 days" },
  { days: 30, label: "30 days" },
  { days: 90, label: "90 days" },
];

const AXIS = { fontSize: 11, fill: "var(--muted-foreground)" };
const TOOLTIP = {
  contentStyle: { background: "var(--popover)", border: "1px solid var(--border)", borderRadius: 8, fontSize: 12, color: "var(--foreground)" },
  cursor: { fill: "var(--muted)", opacity: 0.5 },
};

const num = new Intl.NumberFormat("en-GB");
const compact = new Intl.NumberFormat("en-GB", { notation: "compact", maximumFractionDigits: 1 });

/** USD with enough decimals to read very small LLM costs. */
export function usd(value: number | string | null | undefined, digits = 4): string {
  const n = Number(value ?? 0);
  if (n === 0) return "$0";
  return `$${n < 0.0001 ? n.toExponential(1) : n.toFixed(digits)}`;
}

function ms(value: number | null | undefined) {
  return value === null || value === undefined ? "—" : `${num.format(value)} ms`;
}

/** Key for the selected row: the user id, or "system" for calls without a recorded user. */
type Selection = number | "system";

export function UsageDashboard() {
  const [days, setDays] = useState(30);
  const [selected, setSelected] = useState<Selection | null>(null);
  const overview = useQuery({
    queryKey: ["admin", "usage", days],
    queryFn: () => call<UsageOverview>(api.GET("/admin/usage/users", { params: { query: { days } } })),
  });
  const current = selected ?? (overview.data?.users.find((u) => u.calls > 0)?.userId ?? null);

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <Link href="/app/admin" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="size-3.5" aria-hidden /> AI monitoring
          </Link>
          <h1 className="text-2xl font-semibold tracking-tight">LLM usage per user</h1>
          <p className="text-sm text-muted-foreground">
            Calls, tokens and estimated OpenAI cost by user, from the <code className="font-mono text-xs">ai_call</code> ledger.
          </p>
        </div>
        <div className="flex rounded-lg border bg-card p-0.5 text-sm" role="group" aria-label="Time window">
          {WINDOWS.map((w) => (
            <button
              key={w.days}
              type="button"
              aria-pressed={days === w.days}
              onClick={() => setDays(w.days)}
              className={cn("rounded-md px-3 py-1", days === w.days ? "bg-muted font-medium" : "text-muted-foreground hover:text-foreground")}
              data-testid={`window-${w.days}`}
            >
              {w.label}
            </button>
          ))}
        </div>
      </div>

      {overview.isLoading && <Skeleton className="h-72 rounded-xl" />}
      {overview.isError && (
        <p role="alert" className="rounded-xl border border-bad/30 bg-bad-soft p-4 text-sm text-bad">
          {errorMessage(overview.error)}
        </p>
      )}
      {overview.data && (
        <>
          <ConfigStrip data={overview.data} />
          <TotalsTiles data={overview.data} />
          <UsersTable users={overview.data.users} selected={current} onSelect={setSelected} />
          {current !== null && <UserDetailPanel key={`${current}-${days}`} selection={current} days={days} />}
        </>
      )}
    </div>
  );
}

function ConfigStrip({ data }: { data: UsageOverview }) {
  const c = data.config;
  const [showPricing, setShowPricing] = useState(false);
  const used = Math.min(1, c.budgetUsedToday);
  const tone = used >= 1 ? "bg-bad" : used >= 0.8 ? "bg-warn" : "bg-ok";
  return (
    <section className="rounded-xl border bg-card p-4" aria-labelledby="config-title">
      <h2 id="config-title" className="sr-only">
        OpenAI configuration
      </h2>
      <div className="grid gap-4 md:grid-cols-[1fr_1fr_1.4fr]">
        <div className="flex items-start gap-3">
          <KeyRound className="mt-0.5 size-4 text-cobalt" aria-hidden />
          <div className="text-sm">
            <p className="flex items-center gap-2">
              AI profile
              <StatusPill tone={c.profile === "live" ? "cobalt" : "neutral"}>{c.profile}</StatusPill>
            </p>
            <p className="mt-1 text-muted-foreground">
              Chat <span className="font-mono text-xs text-foreground">{c.chatModel}</span> · embeddings{" "}
              <span className="font-mono text-xs text-foreground">{c.embeddingModel}</span>
            </p>
            {c.profile === "mock" && (
              <p className="mt-1 text-xs text-muted-foreground">Mock calls are priced like the model they simulate; no money is spent.</p>
            )}
          </div>
        </div>
        <div className="flex items-start gap-3">
          <ShieldAlert className="mt-0.5 size-4 text-cobalt" aria-hidden />
          <div className="text-sm">
            <p>Spend protection</p>
            <p className="mt-1 text-muted-foreground">
              {c.rateLimitPerMinute} requests / minute per organization · max_tokens {c.defaultMaxTokens} by default
            </p>
          </div>
        </div>
        <div className="flex items-start gap-3">
          <Gauge className="mt-0.5 size-4 text-cobalt" aria-hidden />
          <div className="w-full text-sm">
            <p className="flex justify-between gap-2">
              <span>Live spend today</span>
              <span className="tabular-nums">
                {usd(c.liveSpendTodayUsd)} / ${Number(c.dailyBudgetUsd).toFixed(2)}
              </span>
            </p>
            <div className="mt-2 h-2 overflow-hidden rounded-full bg-muted" role="meter" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(used * 100)} aria-label="Daily budget used">
              <div className={cn("h-full rounded-full", tone)} style={{ width: `${Math.max(used * 100, used > 0 ? 2 : 0)}%` }} />
            </div>
            <p className="mt-1 text-xs text-muted-foreground">{percent(used)} of the daily budget (UTC day); live calls are refused at 100%.</p>
          </div>
        </div>
      </div>
      <button
        type="button"
        onClick={() => setShowPricing((v) => !v)}
        aria-expanded={showPricing}
        className="mt-3 inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
      >
        Price table used for estimates <ChevronDown className={cn("size-3.5 transition-transform", showPricing && "rotate-180")} aria-hidden />
      </button>
      {showPricing && (
        <table className="mt-2 w-full max-w-lg text-xs">
          <thead className="text-muted-foreground">
            <tr className="border-b">
              <th scope="col" className="py-1 text-left font-medium">Model</th>
              <th scope="col" className="py-1 text-right font-medium">Input / 1M tokens</th>
              <th scope="col" className="py-1 text-right font-medium">Output / 1M tokens</th>
            </tr>
          </thead>
          <tbody>
            {c.pricing.map((p) => (
              <tr key={p.model} className="border-b last:border-0">
                <td className="py-1 font-mono">{p.model}</td>
                <td className="py-1 text-right tabular-nums">${Number(p.inputPerMillion).toFixed(2)}</td>
                <td className="py-1 text-right tabular-nums">${Number(p.outputPerMillion).toFixed(2)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function TotalsTiles({ data }: { data: UsageOverview }) {
  const t = data.totals;
  const success = t.calls ? t.okCalls / t.calls : 0;
  const tiles = [
    { label: "Calls", value: num.format(t.calls), hint: `${percent(success)} succeeded` },
    { label: "Failed", value: num.format(t.failedCalls), hint: "invalid output or provider error", warn: t.failedCalls > 0 },
    { label: "Refused", value: num.format(t.refusedCalls), hint: "budget or rate limit", warn: t.refusedCalls > 0 },
    { label: "Input tokens", value: compact.format(t.inputTokens), hint: num.format(t.inputTokens) },
    { label: "Output tokens", value: compact.format(t.outputTokens), hint: num.format(t.outputTokens) },
    { label: "Estimated cost", value: usd(t.costUsd), hint: `live ${usd(t.liveCostUsd)}` },
    { label: "Projected / month", value: usd(t.projectedMonthlyCostUsd, 2), hint: `at ${usd(t.avgDailyCostUsd)} per day` },
    { label: "Active users", value: String(t.activeUsers), hint: `in the last ${data.days === 1 ? "day" : `${data.days} days`}` },
  ];
  return (
    <dl className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-8" data-testid="usage-totals">
      {tiles.map((tile) => (
        <div key={tile.label} className="rounded-xl border bg-card p-3">
          <dt className="text-xs text-muted-foreground">{tile.label}</dt>
          <dd className={cn("mt-1 text-lg font-semibold tabular-nums", tile.warn && "text-warn")}>{tile.value}</dd>
          <dd className="text-[11px] text-muted-foreground">{tile.hint}</dd>
        </div>
      ))}
    </dl>
  );
}

function selectionOf(u: UserUsage): Selection {
  return u.userId === null ? "system" : u.userId;
}

function UsersTable({ users, selected, onSelect }: { users: UserUsage[]; selected: Selection | null; onSelect: (s: Selection) => void }) {
  return (
    <section className="overflow-x-auto rounded-xl border bg-card" aria-labelledby="users-title">
      <h2 id="users-title" className="flex items-center gap-2 px-4 pt-4 text-sm font-semibold">
        <Users className="size-4 text-muted-foreground" aria-hidden /> Users
        <span className="font-normal text-muted-foreground">— select a row for the detail</span>
      </h2>
      <table className="mt-2 w-full min-w-[980px] text-sm" data-testid="usage-users">
        <thead className="text-xs text-muted-foreground">
          <tr className="border-b">
            <th scope="col" className="px-4 py-2 text-left font-medium">User</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">Calls</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">Success</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">Failed · refused · retries</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">Tokens in / out</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">Est. cost</th>
            <th scope="col" className="px-3 py-2 text-left font-medium">Share of cost</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">Avg / p95</th>
            <th scope="col" className="px-4 py-2 text-left font-medium">Last call</th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => {
            const key = selectionOf(u);
            const isSelected = key === selected;
            return (
              <tr
                key={String(key)}
                onClick={() => onSelect(key)}
                className={cn("cursor-pointer border-b last:border-0 hover:bg-accent/30", isSelected && "bg-accent/50")}
                aria-selected={isSelected}
                data-testid={`usage-row-${u.persona ?? "system"}`}
              >
                <th scope="row" className="px-4 py-2 text-left font-normal">
                  <button type="button" className="text-left" onClick={() => onSelect(key)}>
                    <span className="block font-medium">{u.displayName}</span>
                    <span className="block text-xs text-muted-foreground">
                      {u.organization} · {u.role.toLowerCase()}
                    </span>
                  </button>
                </th>
                <td className="px-3 py-2 text-right tabular-nums">{num.format(u.calls)}</td>
                <td className="px-3 py-2 text-right tabular-nums">{u.calls ? percent(u.okCalls / u.calls) : "—"}</td>
                <td className="px-3 py-2 text-right tabular-nums text-muted-foreground">
                  <span className={cn(u.failedCalls > 0 && "font-medium text-warn")}>{u.failedCalls}</span> ·{" "}
                  <span className={cn(u.refusedCalls > 0 && "font-medium text-bad")}>{u.refusedCalls}</span> · {u.retries}
                </td>
                <td className="px-3 py-2 text-right tabular-nums">
                  {compact.format(u.inputTokens)} / {compact.format(u.outputTokens)}
                </td>
                <td className="px-3 py-2 text-right font-medium tabular-nums">{usd(u.costUsd)}</td>
                <td className="px-3 py-2">
                  <span className="flex items-center gap-2">
                    <span className="h-1.5 w-20 overflow-hidden rounded-full bg-muted" aria-hidden>
                      <span className="block h-full rounded-full bg-cobalt" style={{ width: `${u.shareOfCost * 100}%` }} />
                    </span>
                    <span className="text-xs tabular-nums">{percent(u.shareOfCost)}</span>
                  </span>
                </td>
                <td className="px-3 py-2 text-right tabular-nums text-muted-foreground">
                  {ms(u.avgLatencyMs)} / {ms(u.p95LatencyMs)}
                </td>
                <td className="px-4 py-2 text-xs text-muted-foreground">{u.lastCallAt ? dateTime(u.lastCallAt) : "never"}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </section>
  );
}

function UserDetailPanel({ selection, days }: { selection: Selection; days: number }) {
  const detail = useQuery({
    queryKey: ["admin", "usage", "detail", selection, days],
    queryFn: () =>
      call<UsageUserDetail>(
        api.GET("/admin/usage/detail", { params: { query: { days, userId: selection === "system" ? undefined : selection } } }),
      ),
  });
  if (detail.isLoading) return <Skeleton className="h-96 rounded-xl" />;
  if (detail.isError || !detail.data) {
    return (
      <p role="alert" className="rounded-xl border border-bad/30 bg-bad-soft p-4 text-sm text-bad">
        {errorMessage(detail.error)}
      </p>
    );
  }
  const d = detail.data;
  const u = d.user;
  const daily = d.daily.map((p) => ({ ...p, label: shortDate(p.day) }));
  const tiles = [
    { label: "Calls", value: num.format(u.calls) },
    { label: "Avg tokens / call", value: num.format(d.avgTokensPerCall) },
    { label: "Avg cost / call", value: usd(d.avgCostPerCallUsd, 6) },
    { label: "Output / input tokens", value: d.outputInputRatio.toFixed(2) },
    { label: "Latency avg / p95", value: `${ms(u.avgLatencyMs)} / ${ms(u.p95LatencyMs)}` },
    { label: "Live cost", value: usd(u.liveCostUsd) },
  ];

  return (
    <section className="grid gap-4 rounded-xl border bg-card p-4" aria-labelledby="detail-title" data-testid="usage-detail">
      <div>
        <h2 id="detail-title" className="text-lg font-semibold tracking-tight">
          {u.displayName}
        </h2>
        <p className="text-sm text-muted-foreground">
          {u.organization} · last {d.days === 1 ? "day" : `${d.days} days`}
        </p>
      </div>
      <dl className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        {tiles.map((t) => (
          <div key={t.label} className="rounded-lg border p-3">
            <dt className="text-xs text-muted-foreground">{t.label}</dt>
            <dd className="mt-1 font-semibold tabular-nums">{t.value}</dd>
          </div>
        ))}
      </dl>

      {u.calls === 0 ? (
        <p className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">No AI call in this window.</p>
      ) : (
        <>
          <div>
            <h3 className="text-sm font-semibold">Tokens per day</h3>
            <p className="mb-2 text-xs text-muted-foreground">Input and output tokens are billed at different prices.</p>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={daily} margin={{ left: 0, right: 8, top: 4 }}>
                <CartesianGrid vertical={false} stroke="var(--border)" strokeDasharray="2 4" />
                <XAxis dataKey="label" tick={AXIS} axisLine={false} tickLine={false} interval="preserveStartEnd" minTickGap={16} />
                <YAxis tick={AXIS} axisLine={false} tickLine={false} width={48} tickFormatter={(v: number) => compact.format(v)} />
                <Tooltip {...TOOLTIP} formatter={(v, name) => [num.format(Number(v)), name]} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Bar dataKey="inputTokens" name="Input tokens" stackId="t" fill="var(--chart-1)" />
                <Bar dataKey="outputTokens" name="Output tokens" stackId="t" fill="var(--chart-2)" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          <div className="grid gap-4 xl:grid-cols-2">
            <BreakdownTable title="By feature" rows={d.byFeature} testId="usage-by-feature" />
            <div className="grid content-start gap-4">
              <BreakdownTable title="By model" rows={d.byModel} />
              <div>
                <h3 className="text-sm font-semibold">By status</h3>
                <ul className="mt-2 flex flex-wrap gap-2">
                  {d.byStatus.map((s) => (
                    <li key={s.key}>
                      <StatusPill tone={s.key === "OK" ? "ok" : s.key === "INVALID_OUTPUT" ? "warn" : "bad"}>
                        {s.key.toLowerCase().replace("_", " ")} · {s.calls}
                      </StatusPill>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </div>

          <div className="overflow-x-auto">
            <h3 className="text-sm font-semibold">Recent calls</h3>
            <table className="mt-2 w-full min-w-[900px] text-xs" data-testid="usage-recent">
              <thead className="text-muted-foreground">
                <tr className="border-b">
                  <th scope="col" className="py-1.5 pr-3 text-left font-medium">When</th>
                  <th scope="col" className="py-1.5 pr-3 text-left font-medium">Feature</th>
                  <th scope="col" className="py-1.5 pr-3 text-left font-medium">Model</th>
                  <th scope="col" className="py-1.5 pr-3 text-left font-medium">Status</th>
                  <th scope="col" className="py-1.5 pr-3 text-right font-medium">In / out tokens</th>
                  <th scope="col" className="py-1.5 pr-3 text-right font-medium">Latency</th>
                  <th scope="col" className="py-1.5 pr-3 text-right font-medium">Cost</th>
                  <th scope="col" className="py-1.5 text-left font-medium">Mission</th>
                </tr>
              </thead>
              <tbody>
                {d.recentCalls.map((c) => (
                  <tr key={c.id} className="border-b last:border-0" title={c.error ?? undefined}>
                    <td className="py-1.5 pr-3 whitespace-nowrap text-muted-foreground">{dateTime(c.createdAt)}</td>
                    <td className="py-1.5 pr-3 font-mono">
                      {c.feature}
                      {c.attempt > 1 && <span className="ml-1 text-warn">(retry)</span>}
                    </td>
                    <td className="py-1.5 pr-3 font-mono text-muted-foreground">{c.model}</td>
                    <td className="py-1.5 pr-3">
                      <StatusPill tone={c.status === "OK" ? "ok" : c.status === "INVALID_OUTPUT" ? "warn" : "bad"}>{c.status.toLowerCase()}</StatusPill>
                    </td>
                    <td className="py-1.5 pr-3 text-right tabular-nums">
                      {num.format(c.inputTokens)} / {num.format(c.outputTokens)}
                    </td>
                    <td className="py-1.5 pr-3 text-right tabular-nums">{ms(c.latencyMs)}</td>
                    <td className="py-1.5 pr-3 text-right tabular-nums">{usd(c.costUsd, 6)}</td>
                    <td className="py-1.5 font-mono text-muted-foreground">{c.missionRef ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}

function BreakdownTable({ title, rows, testId }: { title: string; rows: UsageBreakdown[]; testId?: string }) {
  const total = rows.reduce((s, r) => s + Number(r.costUsd), 0);
  return (
    <div className="overflow-x-auto">
      <h3 className="text-sm font-semibold">{title}</h3>
      <table className="mt-2 w-full min-w-[480px] text-xs" data-testid={testId}>
        <thead className="text-muted-foreground">
          <tr className="border-b">
            <th scope="col" className="py-1.5 pr-3 text-left font-medium">{title.replace("By ", "")}</th>
            <th scope="col" className="py-1.5 pr-3 text-right font-medium">Calls</th>
            <th scope="col" className="py-1.5 pr-3 text-right font-medium">Tokens in / out</th>
            <th scope="col" className="py-1.5 pr-3 text-right font-medium">p95</th>
            <th scope="col" className="py-1.5 text-right font-medium">Cost (share)</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.key} className="border-b last:border-0">
              <th scope="row" className="py-1.5 pr-3 text-left font-mono font-normal">
                {r.key}
                {r.failedCalls > 0 && <span className="ml-1 text-warn">· {r.failedCalls} failed</span>}
              </th>
              <td className="py-1.5 pr-3 text-right tabular-nums">{num.format(r.calls)}</td>
              <td className="py-1.5 pr-3 text-right tabular-nums">
                {compact.format(r.inputTokens)} / {compact.format(r.outputTokens)}
              </td>
              <td className="py-1.5 pr-3 text-right tabular-nums">{ms(r.p95LatencyMs)}</td>
              <td className="py-1.5 text-right tabular-nums">
                {usd(r.costUsd)} <span className="text-muted-foreground">({total ? percent(Number(r.costUsd) / total) : "0%"})</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
