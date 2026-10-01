"use client";

import { useQuery } from "@tanstack/react-query";
import { ChevronDown, FileText, Inbox, Users } from "lucide-react";
import { useState } from "react";
import { StatusPill } from "@/components/mission/status-badges";
import { Skeleton } from "@/components/ui/skeleton";
import { api, call, errorMessage } from "@/lib/api/client";
import type { OrderDetail, OrderSummary, SupplierInvoice } from "@/lib/api/types";
import { date, money, PHASE_LABEL, shortDate } from "@/lib/format";
import { cn } from "@/lib/utils";

/** Supplier view: orders published to the supplier, its own proposals and placements, its invoices. */
export function SupplierBoard({ supplierName }: { supplierName: string }) {
  const orders = useQuery({ queryKey: ["supplier", "orders"], queryFn: () => call<OrderSummary[]>(api.GET("/supplier/orders")) });
  const invoices = useQuery({
    queryKey: ["supplier", "invoices"],
    queryFn: () => call<SupplierInvoice[]>(api.GET("/supplier/invoices")),
  });

  return (
    <div className="grid gap-8">
      <section aria-labelledby="orders-title">
        <h1 id="orders-title" className="text-2xl font-semibold tracking-tight">
          Orders for {supplierName}
        </h1>
        <p className="text-sm text-muted-foreground">
          Orders published to your agency by your clients. You only see your own proposals and placements.
        </p>
        <div className="mt-4">
          {orders.isLoading && <Skeleton className="h-24 rounded-xl" />}
          {orders.isError && <ErrorBox message={errorMessage(orders.error)} />}
          {orders.data?.length === 0 && <Empty text="No order has been published to you yet." />}
          {orders.data && orders.data.length > 0 && (
            <ul className="grid gap-3" data-testid="supplier-orders">
              {orders.data.map((o) => (
                <OrderRow key={o.missionId} order={o} />
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

function OrderRow({ order }: { order: OrderSummary }) {
  const [open, setOpen] = useState(false);
  const detail = useQuery({
    queryKey: ["supplier", "order", order.missionId],
    queryFn: () => call<OrderDetail>(api.GET("/supplier/orders/{missionId}", { params: { path: { missionId: order.missionId } } })),
    enabled: open,
  });
  return (
    <li className="rounded-xl border bg-card">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="grid w-full gap-2 p-4 text-left sm:grid-cols-[minmax(0,2fr)_minmax(0,1.4fr)_auto] sm:items-center"
      >
        <span className="min-w-0">
          <span className="block font-mono text-xs text-muted-foreground">
            {order.ref} · {order.client}
          </span>
          <span className="block truncate font-medium">{order.title}</span>
          <span className="block text-sm text-muted-foreground">
            {order.quantity ?? "?"} × {order.position ?? "position"} · {shortDate(order.startDate)} → {date(order.endDate)}
          </span>
        </span>
        <span className="flex flex-wrap items-center gap-2 text-sm">
          <StatusPill tone="cobalt">{PHASE_LABEL[order.phase]}</StatusPill>
          <span className="inline-flex items-center gap-1 text-muted-foreground">
            <FileText className="size-3.5" aria-hidden /> {order.myProposals} proposals
          </span>
          <span className="inline-flex items-center gap-1 text-muted-foreground">
            <Users className="size-3.5" aria-hidden /> {order.myPlacements} placed
          </span>
        </span>
        <ChevronDown className={cn("size-4 text-muted-foreground transition-transform", open && "rotate-180")} aria-hidden />
      </button>
      {open && (
        <div className="border-t p-4">
          {detail.isLoading && <Skeleton className="h-16" />}
          {detail.data && (
            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <h3 className="text-sm font-medium">Your proposals</h3>
                <ul className="mt-2 grid gap-1.5 text-sm">
                  {detail.data.myProposals.map((p) => (
                    <li key={p.candidateId} className="flex items-center justify-between gap-2">
                      <span>{p.workerName}</span>
                      {p.eligible ? (
                        <StatusPill tone={p.selected ? "ok" : "neutral"}>{p.selected ? "Selected" : `Rank ${p.rank}`}</StatusPill>
                      ) : (
                        <StatusPill tone="bad">Excluded</StatusPill>
                      )}
                    </li>
                  ))}
                </ul>
              </div>
              <div>
                <h3 className="text-sm font-medium">Your placements</h3>
                {detail.data.myPlacements.length === 0 ? (
                  <p className="mt-2 text-sm text-muted-foreground">None yet.</p>
                ) : (
                  <ul className="mt-2 grid gap-1.5 text-sm">
                    {detail.data.myPlacements.map((p) => (
                      <li key={p.workerId}>
                        {p.workerName} · {shortDate(p.start)} → {date(p.end)}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </li>
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
