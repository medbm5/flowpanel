"use client";

import { CheckCircle2, ChevronDown, FilePlus2, Loader2, PenLine, Wrench, XCircle } from "lucide-react";
import { useState } from "react";
import { AiBadge, StatusPill } from "@/components/mission/status-badges";
import { Button } from "@/components/ui/button";
import { api, call } from "@/lib/api/client";
import type { ContractsView, ContractView } from "@/lib/api/types";
import { date, money } from "@/lib/format";
import { phaseKeys, useContracts, usePhaseAction } from "@/lib/mission-hooks";
import { cn } from "@/lib/utils";
import { useQueryClient } from "@tanstack/react-query";
import { PhaseError, PhaseLoading, PhaseSection } from "../phase-section";

export function ContractsPhase({ missionId }: { missionId: number }) {
  const contracts = useContracts(missionId);
  const generate = usePhaseAction(missionId, phaseKeys.contracts(missionId), () =>
    call<ContractsView>(api.POST("/missions/{id}/contracts/generate", { params: { path: { id: missionId } } })),
    (v) => `${v.contracts.length} contract(s) drafted`,
  );
  const sign = usePhaseAction(missionId, phaseKeys.contracts(missionId), () =>
    call<ContractsView>(api.POST("/missions/{id}/contracts/sign", { params: { path: { id: missionId } } })),
    () => "Contracts signed by client and supplier",
  );
  if (contracts.isLoading) return <PhaseLoading />;
  if (contracts.isError || !contracts.data) return <PhaseError error={contracts.error} />;
  const view = contracts.data;
  const blocking = view.contracts.some((c) => c.blocking);
  const allSigned = view.generated && view.contracts.every((c) => c.status === "SIGNED");

  return (
    <PhaseSection
      title="Contracts"
      description="One contract per placement. The AI drafts the text; compliance rules check rate, legal reason, certificates and dates."
      readOnly={view.readOnly}
      actions={
        <>
          {!view.generated && (
            <Button onClick={() => generate.mutate(undefined)} disabled={generate.isPending}>
              {generate.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <FilePlus2 aria-hidden />}
              Generate contracts
            </Button>
          )}
          {view.generated && !allSigned && (
            <Button onClick={() => sign.mutate(undefined)} disabled={sign.isPending || blocking} title={blocking ? "Fix blocking issues first" : undefined}>
              {sign.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <PenLine aria-hidden />}
              Sign all (e-signature)
            </Button>
          )}
        </>
      }
    >
      {!view.generated ? (
        <p className="rounded-xl border border-dashed p-6 text-sm text-muted-foreground">
          {view.placements} placement(s) to contract. Generating creates one contract each, checked by the rules.
        </p>
      ) : (
        <ul className="grid gap-3" data-testid="contracts">
          {view.contracts.map((c) => (
            <ContractCard key={c.id} missionId={missionId} contract={c} readOnly={view.readOnly} />
          ))}
        </ul>
      )}
    </PhaseSection>
  );
}

function ContractCard({ missionId, contract: c, readOnly }: { missionId: number; contract: ContractView; readOnly: boolean }) {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
  const fix = usePhaseAction(
    missionId,
    null,
    (ruleId: string) => call<ContractView>(api.POST("/contracts/{contractId}/fix/{ruleId}", { params: { path: { contractId: c.id, ruleId } } })),
    () => `Fix applied on ${c.ref}`,
  );
  return (
    <li className={cn("rounded-xl border bg-card", c.blocking && "border-bad/40")} data-testid="contract-card">
      <div className="flex flex-wrap items-start justify-between gap-3 p-4">
        <div>
          <p className="font-mono text-xs text-muted-foreground">{c.ref}</p>
          <p className="font-medium">
            {c.workerName} <span className="text-sm font-normal text-muted-foreground">· {c.supplierName}</span>
          </p>
          <p className="text-sm text-muted-foreground">
            {date(c.startDate)} → {date(c.endDate)} · {money(c.hourlyRate)}/h · {c.weeklyHours} h/week
          </p>
        </div>
        <StatusPill tone={c.status === "SIGNED" ? "ok" : c.blocking ? "bad" : "neutral"}>
          {c.status === "SIGNED" ? "Signed" : c.blocking ? "Blocking issue" : "Ready to sign"}
        </StatusPill>
      </div>
      <ul className="grid gap-1.5 border-t px-4 py-3 text-sm">
        {c.checks.map((r) => (
          <li key={r.ruleId} className="flex flex-wrap items-center gap-2" data-rule={r.ruleId} data-passed={r.passed}>
            {r.passed ? (
              <CheckCircle2 className="size-4 shrink-0 text-ok" aria-label="Passed" />
            ) : (
              <XCircle className="size-4 shrink-0 text-bad" aria-label="Failed" />
            )}
            <span className="font-medium">{r.label}</span>
            <span className={cn("text-muted-foreground", !r.passed && "text-bad")}>{r.message}</span>
            {!r.passed && r.autoFixable && !readOnly && c.status !== "SIGNED" && (
              <Button
                size="xs"
                variant="outline"
                className="ml-auto"
                disabled={fix.isPending}
                onClick={() =>
                  fix.mutate(r.ruleId, { onSuccess: () => void queryClient.invalidateQueries({ queryKey: phaseKeys.contracts(missionId) }) })
                }
              >
                <Wrench aria-hidden /> Fix
              </Button>
            )}
          </li>
        ))}
      </ul>
      <div className="border-t">
        <button
          type="button"
          className="flex w-full items-center justify-between px-4 py-2 text-sm"
          aria-expanded={open}
          onClick={() => setOpen((v) => !v)}
        >
          <span className="flex items-center gap-2">
            Contract text {c.aiDrafted && <AiBadge label="AI draft" />}
          </span>
          <ChevronDown className={cn("size-4 transition-transform", open && "rotate-180")} aria-hidden />
        </button>
        {open && <pre className="whitespace-pre-wrap break-words px-4 pb-4 font-sans text-sm leading-relaxed text-muted-foreground">{c.content}</pre>}
      </div>
    </li>
  );
}
