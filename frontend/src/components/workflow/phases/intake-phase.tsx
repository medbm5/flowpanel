"use client";

import { AlertTriangle, Check, Loader2, Pencil, Sparkles, X } from "lucide-react";
import { useState } from "react";
import { AiBadge } from "@/components/mission/status-badges";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api, call, errorMessage } from "@/lib/api/client";
import type { FieldView, IntakeView } from "@/lib/api/types";
import { phaseKeys, useIntake, usePhaseAction } from "@/lib/mission-hooks";
import { cn } from "@/lib/utils";
import { ConfidenceBar, PhaseError, PhaseLoading, PhaseSection } from "../phase-section";

export function IntakePhase({ missionId }: { missionId: number }) {
  const intake = useIntake(missionId);
  const extract = usePhaseAction(missionId, phaseKeys.intake(missionId), () =>
    call<IntakeView>(api.POST("/missions/{id}/intake/extract", { params: { path: { id: missionId } } })),
    (v) => `Order extracted — ${v.needsReviewCount} field${v.needsReviewCount === 1 ? "" : "s"} to review`,
  );
  if (intake.isLoading) return <PhaseLoading />;
  if (intake.isError || !intake.data) return <PhaseError error={intake.error} />;
  const view = intake.data;

  return (
    <PhaseSection
      title="Intake"
      description="The AI turns the email into an order draft. Fields it is unsure about, or that fail validation, need your review."
      readOnly={view.readOnly}
      actions={
        <Button onClick={() => extract.mutate(undefined)} disabled={extract.isPending} variant={view.extracted ? "outline" : "default"}>
          {extract.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <Sparkles aria-hidden />}
          {view.extracted ? "Extract again" : "Extract order with AI"}
        </Button>
      }
    >
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.25fr)]">
        <div className="rounded-xl border bg-card">
          <h3 className="border-b px-4 py-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">Email received</h3>
          <pre className="max-h-[480px] overflow-auto whitespace-pre-wrap break-words p-4 font-sans text-sm leading-relaxed" data-testid="source-email">
            {view.emailText}
          </pre>
        </div>
        <div className="rounded-xl border bg-card">
          <div className="flex items-center justify-between gap-2 border-b px-4 py-2">
            <h3 className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Order draft {view.extracted && <AiBadge />}
            </h3>
            {view.extracted && (
              <span className={cn("text-xs", view.needsReviewCount ? "text-warn" : "text-ok")}>
                {view.needsReviewCount ? `${view.needsReviewCount} to review` : "All fields reviewed"}
              </span>
            )}
          </div>
          {!view.extracted ? (
            <p className="p-4 text-sm text-muted-foreground">No draft yet. Run the extraction to get the order fields.</p>
          ) : (
            <ul className="divide-y" data-testid="order-fields">
              {view.fields.map((f) => (
                <FieldRow key={f.name} missionId={missionId} field={f} readOnly={view.readOnly} />
              ))}
            </ul>
          )}
        </div>
      </div>
    </PhaseSection>
  );
}

function FieldRow({ missionId, field, readOnly }: { missionId: number; field: FieldView; readOnly: boolean }) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(field.value);
  const [error, setError] = useState<string | null>(null);
  const confirm = usePhaseAction(
    missionId,
    phaseKeys.intake(missionId),
    (v: string | null) =>
      call<IntakeView>(
        api.POST("/missions/{id}/intake/fields/{field}/confirm", {
          params: { path: { id: missionId, field: field.name } },
          body: v === null ? {} : { value: v },
        }),
      ),
    () => `${field.label} ${editing ? "corrected" : "confirmed"}`,
  );

  function save() {
    setError(null);
    confirm.mutate(value, {
      onSuccess: () => setEditing(false),
      onError: (e) => setError(errorMessage(e)),
    });
  }

  return (
    <li
      className={cn("grid gap-2 px-4 py-2.5 sm:grid-cols-[150px_minmax(0,1fr)_auto] sm:items-center", field.needsReview && "bg-warn-soft/60")}
      data-testid={`field-${field.name}`}
      data-needs-review={field.needsReview}
    >
      <span className="text-xs font-medium text-muted-foreground">{field.label}</span>
      <span className="min-w-0">
        {editing ? (
          <>
            <Input
              value={value}
              onChange={(e) => setValue(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") save();
                if (e.key === "Escape") setEditing(false);
              }}
              aria-label={`New value for ${field.label}`}
              aria-invalid={!!error}
              aria-describedby={error ? `error-${field.name}` : undefined}
              className="h-8"
              autoFocus
            />
            {error && (
              <span id={`error-${field.name}`} role="alert" className="mt-1 block text-xs text-bad">
                {error}
              </span>
            )}
          </>
        ) : (
          <span className="block break-words text-sm">{field.value || <span className="text-muted-foreground">—</span>}</span>
        )}
        <span className="mt-1 flex flex-wrap items-center gap-2">
          <ConfidenceBar value={field.confidence} />
          {field.corrected && <span className="text-xs text-muted-foreground">AI proposed “{field.aiValue || "—"}”</span>}
          {field.confirmed && !field.corrected && <span className="text-xs text-ok">Confirmed</span>}
          {field.errors.map((e) => (
            <span key={e} className="inline-flex items-center gap-1 text-xs text-bad">
              <AlertTriangle className="size-3" aria-hidden /> {e}
            </span>
          ))}
        </span>
      </span>
      {!readOnly && (
        <span className="flex gap-1">
          {editing ? (
            <>
              <Button size="sm" onClick={save} disabled={confirm.isPending}>
                <Check aria-hidden /> Save
              </Button>
              <Button size="icon-sm" variant="ghost" aria-label="Cancel" onClick={() => setEditing(false)}>
                <X aria-hidden />
              </Button>
            </>
          ) : (
            <>
              {field.needsReview && (
                <Button size="sm" variant="outline" onClick={() => confirm.mutate(null)} disabled={confirm.isPending}>
                  <Check aria-hidden /> Confirm
                </Button>
              )}
              <Button
                size="icon-sm"
                variant="ghost"
                aria-label={`Edit ${field.label}`}
                onClick={() => {
                  setValue(field.value);
                  setError(null);
                  setEditing(true);
                }}
              >
                <Pencil aria-hidden />
              </Button>
            </>
          )}
        </span>
      )}
    </li>
  );
}
