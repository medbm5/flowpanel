"use client";

import { CheckCheck, Copy, FileDown, Inbox, Loader2, Send } from "lucide-react";
import { toast } from "sonner";
import { AiBadge, StatusPill } from "@/components/mission/status-badges";
import { Button } from "@/components/ui/button";
import { api, call } from "@/lib/api/client";
import type { InvoicesView, InvoiceView } from "@/lib/api/types";
import { formatHours, money } from "@/lib/format";
import { phaseKeys, useInvoices, usePhaseAction } from "@/lib/mission-hooks";
import { cn } from "@/lib/utils";
import { PhaseError, PhaseLoading, PhaseSection } from "../phase-section";

const STATUS_LABEL: Record<string, string> = {
  MATCH: "Match",
  HOURS_MISMATCH: "Hours mismatch",
  RATE_MISMATCH: "Rate mismatch",
  AMOUNT_MISMATCH: "Amount mismatch",
  UNKNOWN_WORKER: "Unknown worker",
  MISSING_LINE: "Not invoiced",
};

export function InvoicePhase({ missionId }: { missionId: number }) {
  const invoices = useInvoices(missionId);
  const receive = usePhaseAction(missionId, phaseKeys.invoice(missionId), () =>
    call<InvoicesView>(api.POST("/missions/{id}/invoice/receive", { params: { path: { id: missionId } } })),
    (v) => `${v.invoices.length} invoice(s) received and matched`,
  );
  const credit = usePhaseAction(missionId, phaseKeys.invoice(missionId), () =>
    call<InvoicesView>(api.POST("/missions/{id}/invoice/request-credit-note", { params: { path: { id: missionId } } })),
    (v) => `Credit note received: ${money(v.creditNotes)}`,
  );
  const approve = usePhaseAction(missionId, phaseKeys.invoice(missionId), () =>
    call<InvoicesView>(api.POST("/missions/{id}/invoice/approve", { params: { path: { id: missionId } } })),
    () => "Invoices approved for payment",
  );
  if (invoices.isLoading) return <PhaseLoading />;
  if (invoices.isError || !invoices.data) return <PhaseError error={invoices.error} />;
  const view = invoices.data;
  const mismatch = view.invoices.some((i) => i.status === "MISMATCH");
  const approved = view.received && view.invoices.every((i) => i.status === "APPROVED");

  return (
    <PhaseSection
      title="Invoice"
      description="Supplier PDFs are read by the AI, then matched line by line against contract rates and approved hours — in exact arithmetic."
      readOnly={view.readOnly}
      actions={
        <>
          {!view.received && (
            <Button onClick={() => receive.mutate(undefined)} disabled={receive.isPending}>
              {receive.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <Inbox aria-hidden />}
              Receive invoices
            </Button>
          )}
          {mismatch && (
            <Button variant="outline" onClick={() => credit.mutate(undefined)} disabled={credit.isPending}>
              {credit.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <Send aria-hidden />}
              Send credit note request
            </Button>
          )}
          {view.received && !approved && (
            <Button onClick={() => approve.mutate(undefined)} disabled={approve.isPending || mismatch}>
              {approve.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <CheckCheck aria-hidden />}
              Approve invoices
            </Button>
          )}
        </>
      }
    >
      {!view.received ? (
        <p className="rounded-xl border border-dashed p-6 text-sm text-muted-foreground">
          Waiting for the suppliers&apos; invoices for this mission.
        </p>
      ) : (
        <>
          <dl className="grid grid-cols-3 gap-3 text-sm">
            <Stat label="Expected" value={money(view.expectedTotal)} />
            <Stat label="Invoiced" value={money(view.invoicedTotal)} />
            <Stat label="Credit notes" value={view.creditNotes ? `−${money(view.creditNotes)}` : "—"} tone={Number(view.creditNotes) > 0 ? "ok" : undefined} />
          </dl>
          <ul className="grid gap-4" data-testid="invoices">
            {view.invoices.map((i) => (
              <InvoiceCard key={i.id} invoice={i} />
            ))}
          </ul>
        </>
      )}
    </PhaseSection>
  );
}

function Stat({ label, value, tone }: { label: string; value: string; tone?: "ok" }) {
  return (
    <div className="rounded-lg border bg-card px-3 py-2">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className={cn("text-base font-semibold tabular-nums", tone === "ok" && "text-ok")}>{value}</dd>
    </div>
  );
}

function InvoiceCard({ invoice: i }: { invoice: InvoiceView }) {
  return (
    <li className={cn("rounded-xl border bg-card", i.status === "MISMATCH" && "border-warn/40")} data-testid="invoice-card">
      <div className="flex flex-wrap items-center justify-between gap-2 p-4">
        <div>
          <p className="flex items-center gap-2 font-medium">
            {i.ref} {i.aiExtracted && <AiBadge label="AI read" />}
          </p>
          <p className="text-sm text-muted-foreground">{i.supplierName}</p>
        </div>
        <div className="flex items-center gap-2">
          <StatusPill tone={i.status === "MISMATCH" ? "warn" : i.status === "RECEIVED" ? "neutral" : "ok"}>{i.status.toLowerCase()}</StatusPill>
          <Button asChild size="sm" variant="ghost">
            <a href={`/api/invoices/${i.id}/pdf`} target="_blank" rel="noreferrer">
              <FileDown aria-hidden /> PDF
            </a>
          </Button>
        </div>
      </div>
      {i.match && (
        <div className="overflow-x-auto border-t">
          <table className="w-full min-w-[640px] text-sm" data-testid="match-table">
            <caption className="sr-only">Three-way match: contract, approved hours, invoice</caption>
            <thead className="text-xs text-muted-foreground">
              <tr className="border-b">
                <th scope="col" className="px-4 py-2 text-left font-medium">Worker</th>
                <th scope="col" className="px-3 py-2 text-right font-medium">Approved h</th>
                <th scope="col" className="px-3 py-2 text-right font-medium">Invoiced h</th>
                <th scope="col" className="px-3 py-2 text-right font-medium">Rate</th>
                <th scope="col" className="px-3 py-2 text-right font-medium">Expected</th>
                <th scope="col" className="px-3 py-2 text-right font-medium">Invoiced</th>
                <th scope="col" className="px-4 py-2 text-left font-medium">Result</th>
              </tr>
            </thead>
            <tbody>
              {i.match.lines.map((l) => {
                const ok = l.status === "MATCH";
                return (
                  <tr key={l.workerName} className={cn("border-b last:border-0", !ok && "bg-warn-soft/50")}>
                    <th scope="row" className="px-4 py-2 text-left font-medium">{l.workerName}</th>
                    <td className="px-3 py-2 text-right tabular-nums">{formatHours(l.expectedHours)}</td>
                    <td className={cn("px-3 py-2 text-right tabular-nums", l.status === "HOURS_MISMATCH" && "font-semibold text-warn")}>
                      {formatHours(l.invoicedHours)}
                    </td>
                    <td className={cn("px-3 py-2 text-right tabular-nums", l.status === "RATE_MISMATCH" && "font-semibold text-warn")}>
                      {money(l.invoicedRate ?? l.expectedRate)}
                    </td>
                    <td className="px-3 py-2 text-right tabular-nums">{money(l.expectedAmount)}</td>
                    <td className="px-3 py-2 text-right tabular-nums">{money(l.invoicedAmount)}</td>
                    <td className="px-4 py-2">
                      <StatusPill tone={ok ? "ok" : "warn"}>{STATUS_LABEL[l.status] ?? l.status}</StatusPill>
                      {!ok && <span className="ml-2 text-xs text-muted-foreground">{l.message}</span>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      {i.aiMessageDraft && (
        <div className="border-t p-4">
          <div className="flex items-center justify-between gap-2">
            <p className="flex items-center gap-2 text-sm font-medium">
              Message to the supplier <AiBadge label="AI draft" />
            </p>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => {
                void navigator.clipboard?.writeText(i.aiMessageDraft);
                toast.success("Message copied");
              }}
            >
              <Copy aria-hidden /> Copy
            </Button>
          </div>
          <pre className="mt-2 whitespace-pre-wrap rounded-lg bg-muted/60 p-3 font-sans text-sm leading-relaxed" data-testid="credit-note-message">
            {i.aiMessageDraft}
          </pre>
          {i.creditNoteRef && (
            <p className="mt-2 text-sm text-ok">
              Credit note {i.creditNoteRef} received: −{money(i.creditNoteAmount)}
            </p>
          )}
        </div>
      )}
    </li>
  );
}
