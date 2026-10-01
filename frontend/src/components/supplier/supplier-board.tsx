"use client";

import { useQuery } from "@tanstack/react-query";
import { ChevronRight, ClipboardList, FilePenLine, FileText, Inbox, ReceiptText, UserPlus, Users } from "lucide-react";
import Link from "next/link";
import { StatusPill } from "@/components/mission/status-badges";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { api, call, errorMessage } from "@/lib/api/client";
import type { OrderSummary, SupplierInvoice } from "@/lib/api/types";
import { date, money, PHASE_LABEL, shortDate } from "@/lib/format";
import { useSupplierDashboard } from "@/lib/supplier-hooks";

const TODO_ICON = { PROPOSE: UserPlus, SIGN: FilePenLine, TIMESHEET: ClipboardList, CREDIT_NOTE: ReceiptText } as const;

/** Staffing agency home: what to do next, orders from clients, invoices. */
export function SupplierBoard({ supplierName }: { supplierName: string }) {
  const dashboard = useSupplierDashboard();
  const orders = useQuery({ queryKey: ["supplier", "orders"], queryFn: () => call<OrderSummary[]>(api.GET("/supplier/orders")) });
  const invoices = useQuery({
    queryKey: ["supplier", "invoices"],
    queryFn: () => call<SupplierInvoice[]>(api.GET("/supplier/invoices")),
  });
  const d = dashboard.data;

  return (
    <div className="grid gap-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{supplierName}</h1>
          <p className="text-sm text-muted-foreground">
            Orders your clients published to your agency. Propose your workers, sign contracts, submit hours, invoice.
          </p>
        </div>
        <Button asChild variant="outline">
          <Link href="/app/workers">
            <Users aria-hidden /> My workers
          </Link>
        </Button>
      </div>

      {dashboard.isLoading && <Skeleton className="h-24 rounded-xl" />}
      {d && (
        <dl className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6" data-testid="supplier-kpis">
          {[
            { label: "Open orders", value: d.openOrders },
            { label: "Orders to staff", value: d.ordersToStaff, warn: d.ordersToStaff > 0 },
            { label: "Active placements", value: d.activePlacements },
            { label: "Contracts to sign", value: d.contractsToSign, warn: d.contractsToSign > 0 },
            { label: "Timesheets flagged", value: d.timesheetsToFix, warn: d.timesheetsToFix > 0 },
            { label: "Invoices to credit", value: d.invoicesToCredit, warn: d.invoicesToCredit > 0 },
          ].map((k) => (
            <div key={k.label} className="rounded-xl border bg-card p-3">
              <dt className="text-xs text-muted-foreground">{k.label}</dt>
              <dd className={`mt-1 text-2xl font-semibold tabular-nums ${k.warn ? "text-warn" : ""}`}>{k.value}</dd>
            </div>
          ))}
        </dl>
      )}

      {d && d.todo.length > 0 && (
        <section aria-labelledby="todo-title" className="rounded-xl border bg-card">
          <h2 id="todo-title" className="px-4 pt-4 text-sm font-semibold">
            To do
          </h2>
          <ul className="mt-2 divide-y" data-testid="supplier-todo">
            {d.todo.map((t, i) => {
              const Icon = TODO_ICON[t.kind as keyof typeof TODO_ICON] ?? ClipboardList;
              return (
                <li key={i}>
                  <Link href={`/app/orders/${t.missionId}`} className="flex items-center gap-3 px-4 py-3 text-sm hover:bg-accent/30">
                    <Icon className="size-4 shrink-0 text-cobalt" aria-hidden />
                    <span className="min-w-0 flex-1">
                      <span className="block">{t.label}</span>
                      <span className="block text-xs text-muted-foreground">
                        {t.client} · <span className="font-mono">{t.missionRef}</span>
                      </span>
                    </span>
                    <ChevronRight className="size-4 text-muted-foreground" aria-hidden />
                  </Link>
                </li>
              );
            })}
          </ul>
        </section>
      )}

      <section aria-labelledby="orders-title">
        <h2 id="orders-title" className="text-lg font-semibold tracking-tight">
          Orders
        </h2>
        <div className="mt-3">
          {orders.isLoading && <Skeleton className="h-24 rounded-xl" />}
          {orders.isError && <ErrorBox message={errorMessage(orders.error)} />}
          {orders.data?.length === 0 && <Empty text="No order has been published to you yet." />}
          {orders.data && orders.data.length > 0 && (
            <ul className="grid gap-3" data-testid="supplier-orders">
              {orders.data.map((o) => (
                <li key={o.missionId}>
                  <Link
                    href={`/app/orders/${o.missionId}`}
                    className="grid gap-2 rounded-xl border bg-card p-4 transition-colors hover:border-cobalt/40 hover:bg-accent/30 sm:grid-cols-[minmax(0,2fr)_minmax(0,1.4fr)_auto] sm:items-center"
                    data-testid="supplier-order"
                  >
                    <span className="min-w-0">
                      <span className="block font-mono text-xs text-muted-foreground">
                        {o.ref} · {o.client}
                      </span>
                      <span className="block truncate font-medium">{o.title}</span>
                      <span className="block text-sm text-muted-foreground">
                        {o.quantity ?? "?"} × {o.position ?? "position"} · {shortDate(o.startDate)} → {date(o.endDate)}
                      </span>
                    </span>
                    <span className="flex flex-wrap items-center gap-2 text-sm">
                      <StatusPill tone={o.phase === "SOURCING" ? "warn" : "cobalt"}>{PHASE_LABEL[o.phase]}</StatusPill>
                      <span className="inline-flex items-center gap-1 text-muted-foreground">
                        <FileText className="size-3.5" aria-hidden /> {o.myProposals} proposals
                      </span>
                      <span className="inline-flex items-center gap-1 text-muted-foreground">
                        <Users className="size-3.5" aria-hidden /> {o.myPlacements} placed
                      </span>
                    </span>
                    <ChevronRight className="hidden size-4 text-muted-foreground sm:block" aria-hidden />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      <section aria-labelledby="invoices-title">
        <h2 id="invoices-title" className="text-lg font-semibold tracking-tight">
          Your invoices
        </h2>
        <div className="mt-3">
          {invoices.isLoading && <Skeleton className="h-16 rounded-xl" />}
          {invoices.isError && <ErrorBox message={errorMessage(invoices.error)} />}
          {invoices.data?.length === 0 && <Empty text="No invoice yet. Invoices appear once a mission reaches the invoice phase." />}
          {invoices.data && invoices.data.length > 0 && (
            <div className="overflow-x-auto rounded-xl border bg-card">
              <table className="w-full min-w-[560px] text-sm">
                <thead className="text-left text-xs text-muted-foreground">
                  <tr className="border-b">
                    <th className="px-4 py-2 font-medium">Invoice</th>
                    <th className="px-4 py-2 font-medium">Client · mission</th>
                    <th className="px-4 py-2 font-medium">Status</th>
                    <th className="px-4 py-2 text-right font-medium">Invoiced</th>
                    <th className="px-4 py-2 text-right font-medium">Credit note</th>
                    <th className="px-4 py-2 text-right font-medium">Payable</th>
                  </tr>
                </thead>
                <tbody>
                  {invoices.data.map((i) => (
                    <tr key={i.id} className="border-b last:border-0">
                      <td className="px-4 py-2 font-mono text-xs">{i.ref}</td>
                      <td className="px-4 py-2">
                        {i.client} · <span className="font-mono text-xs">{i.missionRef}</span>
                      </td>
                      <td className="px-4 py-2">
                        <StatusPill tone={i.status === "APPROVED" ? "ok" : i.status === "MISMATCH" ? "warn" : "neutral"}>
                          {i.status.toLowerCase()}
                        </StatusPill>
                      </td>
                      <td className="px-4 py-2 text-right tabular-nums">{money(i.total)}</td>
                      <td className="px-4 py-2 text-right tabular-nums">{i.creditNoteAmount ? `−${money(i.creditNoteAmount)}` : "—"}</td>
                      <td className="px-4 py-2 text-right font-medium tabular-nums">{money(i.payable)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function Empty({ text }: { text: string }) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed p-8 text-center text-sm text-muted-foreground">
      <Inbox className="size-6" aria-hidden />
      {text}
    </div>
  );
}

function ErrorBox({ message }: { message: string }) {
  return (
    <p role="alert" className="rounded-xl border border-bad/30 bg-bad-soft p-4 text-sm text-bad">
      {message}
    </p>
  );
}
