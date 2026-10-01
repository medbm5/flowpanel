import type { Phase } from "@/lib/api/types";

export const PHASES: Phase[] = ["INTAKE", "SOURCING", "CONTRACTS", "TIMESHEETS", "INVOICE", "CLOSED"];

export const PHASE_LABEL: Record<Phase, string> = {
  INTAKE: "Intake",
  SOURCING: "Sourcing",
  CONTRACTS: "Contracts",
  TIMESHEETS: "Timesheets",
  INVOICE: "Invoice",
  CLOSED: "Closed",
};

const eur = new Intl.NumberFormat("en-GB", { style: "currency", currency: "EUR" });
const hours = new Intl.NumberFormat("en-GB", { maximumFractionDigits: 2 });
const day = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });
const dayShort = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });
const time = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

export function money(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === "") return "—";
  return eur.format(Number(value));
}

export function formatHours(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === "") return "—";
  return `${hours.format(Number(value))} h`;
}

export function date(value: string | null | undefined): string {
  if (!value) return "—";
  return day.format(new Date(value.length === 10 ? `${value}T00:00:00` : value));
}

export function shortDate(value: string | null | undefined): string {
  if (!value) return "—";
  return dayShort.format(new Date(value.length === 10 ? `${value}T00:00:00` : value));
}

export function dateTime(value: string | null | undefined): string {
  if (!value) return "—";
  return time.format(new Date(value));
}

export function percent(value: number): string {
  return `${Math.round(value * 100)}%`;
}
