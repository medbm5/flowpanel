"use client";

import { ArrowLeft, Loader2, Pencil, UserPlus } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { StatusPill } from "@/components/mission/status-badges";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { api, ApiError, call, errorMessage } from "@/lib/api/client";
import type { PoolWorker, WorkerForm } from "@/lib/api/types";
import { date } from "@/lib/format";
import { useSupplierAction, useWorkerPool } from "@/lib/supplier-hooks";

const COMMON_CERTS = ["CACES R489 cat. 1", "CACES R489 cat. 3", "CACES R489 cat. 5", "SST", "Habilitation électrique B1V"];

/** The agency's own pool: add workers, keep availability and certificates up to date. */
export function WorkerPoolPage() {
  const pool = useWorkerPool();
  const [editing, setEditing] = useState<PoolWorker | "new" | null>(null);

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <Link href="/app" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="size-3.5" aria-hidden /> Orders
          </Link>
          <h1 className="text-2xl font-semibold tracking-tight">My workers</h1>
          <p className="text-sm text-muted-foreground">
            Your agency&apos;s pool. Clients only see a worker when you propose them on one of their orders.
          </p>
        </div>
        <Button onClick={() => setEditing("new")} data-testid="add-worker">
          <UserPlus aria-hidden /> Add worker
        </Button>
      </div>

      {pool.isLoading && <Skeleton className="h-64 rounded-xl" />}
      {pool.isError && (
        <p role="alert" className="rounded-xl border border-bad/30 bg-bad-soft p-4 text-sm text-bad">
          {errorMessage(pool.error)}
        </p>
      )}
      {pool.data && (
        <div className="overflow-x-auto rounded-xl border bg-card">
          <table className="w-full min-w-[860px] text-sm" data-testid="worker-pool">
            <thead className="text-xs text-muted-foreground">
              <tr className="border-b">
                <th scope="col" className="px-4 py-2 text-left font-medium">Worker</th>
                <th scope="col" className="px-3 py-2 text-left font-medium">City</th>
                <th scope="col" className="px-3 py-2 text-left font-medium">Skills</th>
                <th scope="col" className="px-3 py-2 text-left font-medium">Certifications</th>
                <th scope="col" className="px-3 py-2 text-right font-medium">Experience</th>
                <th scope="col" className="px-3 py-2 text-left font-medium">Available</th>
                <th scope="col" className="px-3 py-2 text-left font-medium">Placement</th>
                <th scope="col" className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {pool.data.workers.map((w) => (
                <tr key={w.id} className="border-b last:border-0">
                  <th scope="row" className="px-4 py-2 text-left font-normal">
                    <span className="block font-medium">
                      {w.firstName} {w.lastName}
                    </span>
                    <span className="block text-xs text-muted-foreground">
                      {w.email} · {w.phone}
                    </span>
                  </th>
                  <td className="px-3 py-2">{w.city}</td>
                  <td className="px-3 py-2 text-xs">{w.skills.join(", ")}</td>
                  <td className="px-3 py-2">
                    <span className="flex flex-wrap gap-1">
                      {w.certifications.length === 0 && <span className="text-xs text-muted-foreground">—</span>}
                      {w.certifications.map((c) => (
                        <StatusPill key={c} tone="neutral">
                          {c}
                        </StatusPill>
                      ))}
                    </span>
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums">{w.experienceYears} y</td>
                  <td className="px-3 py-2 text-xs">
                    {date(w.availableFrom)} → {w.availableTo ? date(w.availableTo) : "open"}
                  </td>
                  <td className="px-3 py-2">
                    {w.activePlacements > 0 ? (
                      <StatusPill tone="cobalt">Placed until {date(w.placedUntil)}</StatusPill>
                    ) : (
                      <StatusPill tone="ok">Free</StatusPill>
                    )}
                  </td>
                  <td className="px-3 py-2">
                    <Button size="icon-sm" variant="ghost" aria-label={`Edit ${w.firstName} ${w.lastName}`} onClick={() => setEditing(w)}>
                      <Pencil aria-hidden />
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && pool.data && (
        <WorkerDialog
          key={editing === "new" ? "new" : editing.id}
          worker={editing === "new" ? null : editing}
          cities={pool.data.cities}
          onClose={() => setEditing(null)}
        />
      )}
    </div>
  );
}

function WorkerDialog({ worker, cities, onClose }: { worker: PoolWorker | null; cities: string[]; onClose: () => void }) {
  const [form, setForm] = useState({
    firstName: worker?.firstName ?? "",
    lastName: worker?.lastName ?? "",
    email: worker?.email ?? "",
    phone: worker?.phone ?? "",
    city: worker?.city ?? "",
    skills: worker?.skills.join(", ") ?? "",
    certifications: worker?.certifications ?? [],
    experienceYears: String(worker?.experienceYears ?? 0),
    availableFrom: worker?.availableFrom ?? new Date().toISOString().slice(0, 10),
    availableTo: worker?.availableTo ?? "",
    profile: worker?.profile ?? "",
  });
  const [errors, setErrors] = useState<string[]>([]);
  const save = useSupplierAction(
    null,
    (body: WorkerForm) =>
      call<PoolWorker>(
        worker
          ? api.PUT("/supplier/workers/{workerId}", { params: { path: { workerId: worker.id } }, body })
          : api.POST("/supplier/workers", { body }),
      ),
    (w) => `${w.firstName} ${w.lastName} ${worker ? "updated" : "added to your pool"}`,
  );
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  function submit(e: React.FormEvent) {
    e.preventDefault();
    setErrors([]);
    save.mutate(
      {
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email,
        phone: form.phone,
        city: form.city,
        skills: form.skills.split(",").map((s) => s.trim()).filter(Boolean),
        certifications: form.certifications,
        experienceYears: Number(form.experienceYears) || 0,
        availableFrom: form.availableFrom,
        availableTo: form.availableTo || undefined,
        profile: form.profile,
      },
      {
        onSuccess: onClose,
        onError: (err) => {
          const list = err instanceof ApiError && Array.isArray(err.problem.errors) ? (err.problem.errors as string[]) : [errorMessage(err)];
          setErrors(list);
        },
      },
    );
  }

  return (
    <Dialog open onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-xl">
        <form onSubmit={submit} className="grid gap-4">
          <DialogHeader>
            <DialogTitle>{worker ? `Edit ${worker.firstName} ${worker.lastName}` : "Add a worker"}</DialogTitle>
            <DialogDescription>Names, emails and phone numbers are masked before any AI call.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="First name" id="w-first">
              <Input id="w-first" value={form.firstName} onChange={set("firstName")} required />
            </Field>
            <Field label="Last name" id="w-last">
              <Input id="w-last" value={form.lastName} onChange={set("lastName")} required />
            </Field>
            <Field label="Email" id="w-email">
              <Input id="w-email" type="email" value={form.email} onChange={set("email")} required />
            </Field>
            <Field label="Phone" id="w-phone">
              <Input id="w-phone" value={form.phone} onChange={set("phone")} required />
            </Field>
            <Field label="City" id="w-city">
              <select id="w-city" value={form.city} onChange={set("city")} required className="h-9 rounded-lg border bg-background px-2 text-sm">
                <option value="">Choose…</option>
                {cities.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Years of experience" id="w-exp">
              <Input id="w-exp" type="number" min={0} max={50} value={form.experienceYears} onChange={set("experienceYears")} />
            </Field>
            <Field label="Available from" id="w-from">
              <Input id="w-from" type="date" value={form.availableFrom} onChange={set("availableFrom")} required />
            </Field>
            <Field label="Available until (optional)" id="w-to">
              <Input id="w-to" type="date" value={form.availableTo} onChange={set("availableTo")} />
            </Field>
          </div>
          <Field label="Skills (comma separated)" id="w-skills">
            <Input id="w-skills" value={form.skills} onChange={set("skills")} placeholder="cariste, préparation de commandes" />
          </Field>
          <fieldset>
            <legend className="text-sm font-medium">Certifications</legend>
            <div className="mt-2 flex flex-wrap gap-2">
              {Array.from(new Set([...COMMON_CERTS, ...form.certifications])).map((c) => (
                <label key={c} className="flex items-center gap-1.5 rounded-lg border px-2 py-1 text-sm">
                  <input
                    type="checkbox"
                    checked={form.certifications.includes(c)}
                    onChange={(e) =>
                      setForm((f) => ({
                        ...f,
                        certifications: e.target.checked ? [...f.certifications, c] : f.certifications.filter((x) => x !== c),
                      }))
                    }
                    className="accent-[var(--cobalt)]"
                  />
                  {c}
                </label>
              ))}
            </div>
          </fieldset>
          <Field label="Profile (used for matching)" id="w-profile">
            <Textarea id="w-profile" rows={3} value={form.profile} onChange={set("profile")} placeholder="Experience, sites, equipment…" />
          </Field>
          {errors.length > 0 && (
            <ul role="alert" className="grid gap-1 rounded-lg border border-bad/30 bg-bad-soft p-3 text-sm text-bad">
              {errors.map((e) => (
                <li key={e}>{e}</li>
              ))}
            </ul>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={save.isPending} data-testid="save-worker">
              {save.isPending && <Loader2 className="animate-spin" aria-hidden />} {worker ? "Save" : "Add worker"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function Field({ label, id, children }: { label: string; id: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
    </div>
  );
}
