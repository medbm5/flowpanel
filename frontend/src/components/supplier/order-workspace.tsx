"use client";

import { ArrowLeft, CheckCircle2, FileDown, Loader2, MapPin, PenLine, ReceiptText, Save, Undo2, UserMinus, UserPlus, XCircle } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { AiBadge, StatusPill } from "@/components/mission/status-badges";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { api, call, errorMessage } from "@/lib/api/client";
import type { CandidateView, ContractView, InvoiceView, SupplierWorkspace, TimesheetView } from "@/lib/api/types";
import { date, formatHours, money, PHASE_LABEL, shortDate } from "@/lib/format";
import { useSupplierAction, useWorkerPool, useWorkspace } from "@/lib/supplier-hooks";
import { cn } from "@/lib/utils";

type Tab = "order" | "proposals" | "contracts" | "timesheets" | "invoices";

const PHASE_TAB: Record<string, Tab> = {
  SOURCING: "proposals",
  CONTRACTS: "contracts",
  TIMESHEETS: "timesheets",
  INVOICE: "invoices",
};

const ORDER = ["INTAKE", "SOURCING", "CONTRACTS", "TIMESHEETS", "INVOICE", "CLOSED"];
const reached = (phase: string, target: string) => ORDER.indexOf(phase) >= ORDER.indexOf(target);

export function OrderWorkspace({ missionId }: { missionId: number }) {
  const ws = useWorkspace(missionId);
  const [tab, setTab] = useState<Tab | null>(null);
  if (ws.isLoading) return <Skeleton className="h-96 rounded-xl" />;
  if (ws.isError || !ws.data) {
    return (
      <p role="alert" className="rounded-xl border border-bad/30 bg-bad-soft p-4 text-sm text-bad">
        {errorMessage(ws.error)}
      </p>
    );
  }
  const w = ws.data;
  const o = w.order;
  const current: Tab = tab ?? PHASE_TAB[o.phase] ?? "order";

  return (
    <div className="grid gap-5">
      <div>
        <Link href="/app" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="size-3.5" aria-hidden /> Orders
        </Link>
        <div className="mt-1 flex flex-wrap items-center gap-2">
          <span className="font-mono text-sm text-muted-foreground">{o.ref}</span>
          <StatusPill tone="cobalt">{PHASE_LABEL[o.phase]}</StatusPill>
          <span className="text-sm text-muted-foreground">for {o.client}</span>
        </div>
        <h1 className="text-2xl font-semibold tracking-tight">{o.title}</h1>
        <p className="flex flex-wrap items-center gap-1 text-sm text-muted-foreground">
          <MapPin className="size-3.5" aria-hidden /> {o.site} · {o.quantity ?? "?"} position(s) · {shortDate(o.startDate)} →{" "}
          {date(o.endDate)}
          {w.terms && <> · {money(w.terms.hourlyRate)}/h</>}
        </p>
      </div>

      <Tabs value={current} onValueChange={(v) => setTab(v as Tab)}>
        <TabsList className="flex w-full flex-wrap justify-start" aria-label="Order sections">
          <TabsTrigger value="order">Order</TabsTrigger>
          <TabsTrigger value="proposals" data-testid="tab-proposals">
            Proposals ({w.proposals.length})
          </TabsTrigger>
          <TabsTrigger value="contracts" disabled={!reached(o.phase, "CONTRACTS")} data-testid="tab-contracts">
            Contracts ({w.contracts.length})
          </TabsTrigger>
          <TabsTrigger value="timesheets" disabled={!reached(o.phase, "TIMESHEETS")} data-testid="tab-timesheets">
            Timesheets ({w.timesheets.length})
          </TabsTrigger>
          <TabsTrigger value="invoices" disabled={!reached(o.phase, "INVOICE")} data-testid="tab-invoices">
            Invoices ({w.invoices.length})
          </TabsTrigger>
        </TabsList>
        <TabsContent value="order" className="mt-4">
          <OrderTerms ws={w} />
        </TabsContent>
        <TabsContent value="proposals" className="mt-4">
          <Proposals missionId={missionId} ws={w} />
        </TabsContent>
        <TabsContent value="contracts" className="mt-4">
          <Contracts missionId={missionId} ws={w} />
        </TabsContent>
        <TabsContent value="timesheets" className="mt-4">
          <Timesheets missionId={missionId} ws={w} />
        </TabsContent>
        <TabsContent value="invoices" className="mt-4">
          <Invoices missionId={missionId} ws={w} />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function OrderTerms({ ws }: { ws: SupplierWorkspace }) {
  const t = ws.terms;
  if (!t) return <p className="text-sm text-muted-foreground">The order is not confirmed yet.</p>;
  const rows: [string, string][] = [
    ["Position", t.position],
    ["Quantity", String(t.quantity ?? "—")],
    ["Site", t.site],
    ["Period", `${date(t.startDate)} → ${date(t.endDate)}`],
    ["Schedule", t.schedule],
    ["Hours per week", formatHours(t.weeklyHours)],
    ["Hourly rate", money(t.hourlyRate)],
    ["Legal reason", t.legalReason],
    ["Required certifications", t.requiredCertifications.length ? t.requiredCertifications.join(", ") : "None"],
    ["Overtime agreed", t.overtimeAllowed ? "Yes" : "No"],
  ];
  return (
    <dl className="grid gap-x-8 gap-y-3 rounded-xl border bg-card p-4 text-sm sm:grid-cols-2">
      {rows.map(([k, v]) => (
        <div key={k} className="grid grid-cols-[160px_1fr] gap-2">
          <dt className="text-muted-foreground">{k}</dt>
          <dd>{v}</dd>
        </div>
      ))}
    </dl>
  );
}

// ------------------------------------------------------------------ proposals

function Proposals({ missionId, ws }: { missionId: number; ws: SupplierWorkspace }) {
  const pool = useWorkerPool();
  const [workerId, setWorkerId] = useState<string>("");
  const propose = useSupplierAction(
    missionId,
    (id: number) =>
      call<SupplierWorkspace>(api.POST("/supplier/orders/{missionId}/proposals", { params: { path: { missionId } }, body: { workerId: id } })),
    (result, id) => {
      const p = result.proposals.find((x) => x.workerId === id);
      return p?.eligible ? `${p.workerName} proposed — rank ${p.rank}` : `${p?.workerName ?? "Worker"} proposed but excluded by the rules`;
    },
  );
  const proposed = new Set(ws.proposals.map((p) => p.workerId));
  const available = (pool.data?.workers ?? []).filter((w) => !proposed.has(w.id));

  return (
    <div className="grid gap-4">
      {ws.canPropose ? (
        <form
          className="flex flex-wrap items-end gap-2 rounded-xl border bg-card p-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (workerId) propose.mutate(Number(workerId), { onSuccess: () => setWorkerId("") });
          }}
        >
          <label className="grid min-w-64 flex-1 gap-1 text-sm">
            <span className="font-medium">Propose one of your workers</span>
            <select
              value={workerId}
              onChange={(e) => setWorkerId(e.target.value)}
              className="h-9 rounded-lg border bg-background px-2 text-sm"
              data-testid="propose-worker"
            >
              <option value="">Choose a worker…</option>
              {available.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.firstName} {w.lastName} — {w.city} · {w.certifications.join(", ") || "no certification"}
                </option>
              ))}
            </select>
          </label>
          <Button type="submit" disabled={!workerId || propose.isPending} data-testid="propose-submit">
            {propose.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <UserPlus aria-hidden />} Propose
          </Button>
          <p className="w-full text-xs text-muted-foreground">
            The client&apos;s rules check certifications, availability and other placements; the score ranks your proposal against
            every agency&apos;s. <Link className="underline" href="/app/workers">Manage your workers</Link>
          </p>
        </form>
      ) : (
        <p className="rounded-lg border bg-muted/50 px-3 py-2 text-xs text-muted-foreground">
          Proposals are closed: the order is in phase {PHASE_LABEL[ws.order.phase]}.
        </p>
      )}

      {ws.proposals.length === 0 ? (
        <p className="rounded-xl border border-dashed p-6 text-center text-sm text-muted-foreground">You have not proposed anyone yet.</p>
      ) : (
        <ul className="grid gap-3 lg:grid-cols-2" data-testid="my-proposals">
          {ws.proposals.map((p) => (
            <ProposalCard key={p.candidateId} missionId={missionId} proposal={p} canWithdraw={ws.canPropose} />
          ))}
        </ul>
      )}
    </div>
  );
}

function ProposalCard({ missionId, proposal: p, canWithdraw }: { missionId: number; proposal: CandidateView; canWithdraw: boolean }) {
  const withdraw = useSupplierAction(
    missionId,
    () =>
      call<SupplierWorkspace>(
        api.DELETE("/supplier/orders/{missionId}/proposals/{candidateId}", { params: { path: { missionId, candidateId: p.candidateId } } }),
      ),
    () => `${p.workerName} withdrawn`,
  );
  return (
    <li className={cn("grid gap-2 rounded-xl border bg-card p-4", p.selected && "border-ok/50 ring-1 ring-ok/30")} data-testid="my-proposal">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="font-medium">{p.workerName}</p>
          <p className="text-xs text-muted-foreground">{p.city}</p>
        </div>
        {p.selected ? (
          <StatusPill tone="ok">Selected by the client</StatusPill>
        ) : p.eligible ? (
          <StatusPill tone="cobalt">
            Rank {p.rank} · score {p.score.toFixed(0)}
          </StatusPill>
        ) : (
          <StatusPill tone="bad">Excluded</StatusPill>
        )}
      </div>
      <ul className="grid gap-1 text-sm">
        {(p.eligible ? p.explanation : p.exclusionReasons.map((r) => ({ ok: false, text: r }))).map((item) => (
          <li key={item.text} className="flex items-start gap-1.5">
            {item.ok ? (
              <CheckCircle2 className="mt-0.5 size-3.5 shrink-0 text-ok" aria-label="Meets" />
            ) : (
              <XCircle className={cn("mt-0.5 size-3.5 shrink-0", p.eligible ? "text-warn" : "text-bad")} aria-label="Does not meet" />
            )}
            <span>{item.text}</span>
          </li>
        ))}
      </ul>
      {p.aiSummary && (
        <p className="flex items-start gap-2 text-sm text-muted-foreground">
          <AiBadge className="mt-0.5 shrink-0" /> {p.aiSummary}
        </p>
      )}
      {canWithdraw && !p.selected && (
        <Button size="sm" variant="outline" className="w-fit" onClick={() => withdraw.mutate(undefined)} disabled={withdraw.isPending}>
          <UserMinus aria-hidden /> Withdraw
        </Button>
      )}
    </li>
  );
}

// ------------------------------------------------------------------ contracts

function Contracts({ missionId, ws }: { missionId: number; ws: SupplierWorkspace }) {
  if (ws.contracts.length === 0) {
    return <p className="rounded-xl border border-dashed p-6 text-center text-sm text-muted-foreground">No contract for your agency yet.</p>;
  }
  return (
    <ul className="grid gap-3" data-testid="my-contracts">
      {ws.contracts.map((c) => (
        <ContractCard key={c.id} missionId={missionId} contract={c} canSign={ws.canSignContracts} />
      ))}
    </ul>
  );
}

function ContractCard({ missionId, contract: c, canSign }: { missionId: number; contract: ContractView; canSign: boolean }) {
  const [open, setOpen] = useState(false);
  const sign = useSupplierAction(
    missionId,
    () => call<ContractView>(api.POST("/supplier/contracts/{contractId}/sign", { params: { path: { contractId: c.id } } })),
    () => `${c.ref} signed by your agency`,
  );
  return (
    <li className="rounded-xl border bg-card" data-testid="my-contract">
      <div className="flex flex-wrap items-start justify-between gap-3 p-4">
        <div>
          <p className="font-mono text-xs text-muted-foreground">{c.ref}</p>
          <p className="font-medium">{c.workerName}</p>
          <p className="text-sm text-muted-foreground">
            {date(c.startDate)} → {date(c.endDate)} · {money(c.hourlyRate)}/h · {c.weeklyHours} h/week
          </p>
        </div>
        <div className="grid justify-items-end gap-1 text-xs">
          <StatusPill tone={c.signedBySupplierAt ? "ok" : "neutral"}>Agency: {c.signedBySupplierAt ? "signed" : "to sign"}</StatusPill>
          <StatusPill tone={c.signedByClientAt ? "ok" : "neutral"}>Client: {c.signedByClientAt ? "signed" : "pending"}</StatusPill>
        </div>
      </div>
      <ul className="grid gap-1 border-t px-4 py-3 text-sm">
        {c.checks.map((r) => (
          <li key={r.ruleId} className="flex flex-wrap items-center gap-2">
            {r.passed ? <CheckCircle2 className="size-4 text-ok" aria-label="Passed" /> : <XCircle className="size-4 text-bad" aria-label="Failed" />}
            <span className="font-medium">{r.label}</span>
            <span className={cn("text-muted-foreground", !r.passed && "text-bad")}>{r.message}</span>
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap items-center gap-2 border-t px-4 py-3">
        {canSign && !c.signedBySupplierAt && (
          <Button size="sm" onClick={() => sign.mutate(undefined)} disabled={sign.isPending || c.blocking} data-testid="supplier-sign">
            {sign.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <PenLine aria-hidden />} Sign for the agency
          </Button>
        )}
        {c.blocking && <span className="text-xs text-bad">Blocking issue — the client must fix it before signature.</span>}
        <button type="button" className="ml-auto text-sm text-muted-foreground underline-offset-2 hover:underline" onClick={() => setOpen((v) => !v)}>
          {open ? "Hide" : "Read"} contract text
        </button>
      </div>
      {open && <pre className="whitespace-pre-wrap px-4 pb-4 font-sans text-sm leading-relaxed text-muted-foreground">{c.content}</pre>}
    </li>
  );
}

// ------------------------------------------------------------------ timesheets

const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

function Timesheets({ missionId, ws }: { missionId: number; ws: SupplierWorkspace }) {
  if (ws.timesheets.length === 0) {
    return <p className="rounded-xl border border-dashed p-6 text-center text-sm text-muted-foreground">No timesheet for your agency.</p>;
  }
  return (
    <div className="grid gap-3">
      {ws.anomalies.filter((a) => a.status === "OPEN").map((a) => (
        <p key={a.id} className="rounded-xl border border-warn/40 bg-warn-soft/60 p-3 text-sm">
          <span className="font-medium">
            {a.workerName} · week of {shortDate(a.weekStart)}:
          </span>{" "}
          {a.message}. The client may approve it as overtime or return it to you — you can correct the hours below.
        </p>
      ))}
      <p className="text-xs text-muted-foreground">
        Submit the hours actually worked. Saving a sheet asks the client to run the checks again.
      </p>
      <div className="overflow-x-auto rounded-xl border bg-card">
        <table className="w-full min-w-[820px] text-sm" data-testid="supplier-timesheets">
          <thead className="text-xs text-muted-foreground">
            <tr className="border-b">
              <th scope="col" className="px-3 py-2 text-left font-medium">Worker · week</th>
              {DAYS.map((d) => (
                <th key={d} scope="col" className="px-1 py-2 text-center font-medium">
                  {d}
                </th>
              ))}
              <th scope="col" className="px-3 py-2 text-right font-medium">Total</th>
              <th scope="col" className="px-3 py-2 text-left font-medium">Status</th>
              <th scope="col" className="px-3 py-2" />
            </tr>
          </thead>
          <tbody>
            {ws.timesheets.map((t) => (
              <TimesheetRow key={t.id} missionId={missionId} sheet={t} editable={ws.canEditTimesheets && t.status !== "APPROVED"} />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function TimesheetRow({ missionId, sheet: t, editable }: { missionId: number; sheet: TimesheetView; editable: boolean }) {
  const initial = t.dailyHours.map((h) => String(Number(h)));
  const [hours, setHours] = useState<string[]>(initial);
  const dirty = hours.some((h, i) => h !== initial[i]);
  const total = hours.reduce((s, h) => s + (Number(h.replace(",", ".")) || 0), 0);
  const save = useSupplierAction(
    missionId,
    () =>
      call<TimesheetView>(
        api.PUT("/supplier/timesheets/{timesheetId}", {
          params: { path: { timesheetId: t.id } },
          body: { dailyHours: hours.map((h) => Number(h.replace(",", ".")) || 0) },
        }),
      ),
    () => `Hours submitted for ${t.workerName}, week of ${shortDate(t.weekStart)}`,
  );
  return (
    <tr className="border-b last:border-0" data-testid="supplier-timesheet">
      <th scope="row" className="px-3 py-2 text-left font-normal">
        <span className="block font-medium">{t.workerName}</span>
        <span className="text-xs text-muted-foreground">Week of {shortDate(t.weekStart)}</span>
      </th>
      {hours.map((h, i) => (
        <td key={i} className={cn("px-1 py-1.5 text-center", t.flaggedDays.includes(i) && "bg-warn-soft")}>
          {editable ? (
            <input
              inputMode="decimal"
              value={h}
              onChange={(e) => setHours((prev) => prev.map((v, j) => (j === i ? e.target.value : v)))}
              aria-label={`${DAYS[i]} hours for ${t.workerName}, week of ${t.weekStart}`}
              className="h-8 w-12 rounded-md border bg-background text-center tabular-nums"
            />
          ) : (
            <span className="tabular-nums">{Number(h) === 0 ? "–" : h}</span>
          )}
        </td>
      ))}
      <td className={cn("px-3 py-2 text-right font-medium tabular-nums", total > Number(t.contractedHours) && "text-warn")}>
        {formatHours(total)}
        <span className="block text-xs font-normal text-muted-foreground">of {formatHours(t.contractedHours)}</span>
      </td>
      <td className="px-3 py-2">
        <StatusPill tone={t.status === "APPROVED" ? "ok" : t.status === "CORRECTED" ? "cobalt" : "neutral"}>{t.status.toLowerCase()}</StatusPill>
      </td>
      <td className="px-3 py-2">
        {editable && dirty && (
          <span className="flex gap-1">
            <Button size="sm" onClick={() => save.mutate(undefined)} disabled={save.isPending} data-testid="timesheet-save">
              <Save aria-hidden /> Submit
            </Button>
            <Button size="icon-sm" variant="ghost" aria-label="Undo changes" onClick={() => setHours(initial)}>
              <Undo2 aria-hidden />
            </Button>
          </span>
        )}
      </td>
    </tr>
  );
}

// ------------------------------------------------------------------ invoices

function Invoices({ missionId, ws }: { missionId: number; ws: SupplierWorkspace }) {
  if (ws.invoices.length === 0) {
    return <p className="rounded-xl border border-dashed p-6 text-center text-sm text-muted-foreground">No invoice for this order yet.</p>;
  }
  return (
    <ul className="grid gap-3" data-testid="my-invoices">
      {ws.invoices.map((i) => (
        <InvoiceCard key={i.id} missionId={missionId} invoice={i} canCredit={ws.canIssueCreditNotes} />
      ))}
    </ul>
  );
}

function InvoiceCard({ missionId, invoice: i, canCredit }: { missionId: number; invoice: InvoiceView; canCredit: boolean }) {
  const credit = useSupplierAction(
    missionId,
    () => call<InvoiceView>(api.POST("/supplier/invoices/{invoiceId}/credit-note", { params: { path: { invoiceId: i.id } } })),
    (r) => `Credit note ${r.creditNoteRef} issued`,
  );
  return (
    <li className="rounded-xl border bg-card p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-medium">{i.ref}</p>
        <StatusPill tone={i.status === "MISMATCH" ? "warn" : i.status === "APPROVED" ? "ok" : "neutral"}>{i.status.toLowerCase()}</StatusPill>
      </div>
      {i.match && (
        <ul className="mt-2 grid gap-1 text-sm">
          {i.match.lines.map((l) => (
            <li key={l.workerName} className="flex flex-wrap gap-2">
              <span className="font-medium">{l.workerName}</span>
              <span className={cn(l.status === "MATCH" ? "text-ok" : "text-warn")}>{l.message}</span>
            </li>
          ))}
        </ul>
      )}
      {i.aiMessageDraft && i.status === "MISMATCH" && (
        <div className="mt-3">
          <p className="text-xs font-medium text-muted-foreground">Request from the client</p>
          <pre className="mt-1 whitespace-pre-wrap rounded-lg bg-muted/60 p-3 font-sans text-sm leading-relaxed">{i.aiMessageDraft}</pre>
        </div>
      )}
      {i.creditNoteRef && (
        <p className="mt-2 text-sm text-ok">
          Credit note {i.creditNoteRef}: −{money(i.creditNoteAmount)}
        </p>
      )}
      <div className="mt-3 flex flex-wrap gap-2">
        {canCredit && i.status === "MISMATCH" && (
          <Button size="sm" onClick={() => credit.mutate(undefined)} disabled={credit.isPending} data-testid="issue-credit-note">
            <ReceiptText aria-hidden /> Issue credit note
          </Button>
        )}
        <Button asChild size="sm" variant="ghost">
          <a href={`/api/invoices/${i.id}/pdf`} target="_blank" rel="noreferrer">
            <FileDown aria-hidden /> PDF
          </a>
        </Button>
      </div>
    </li>
  );
}
