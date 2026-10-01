import type { components } from "./schema";

/** Aliases over the generated OpenAPI schema (no hand-written API types). */
type S = components["schemas"];

export type Me = S["Me"];
export type Persona = S["Persona"];
export type MissionSummary = S["MissionSummary"];
export type MissionDetail = S["MissionDetail"];
export type PhaseStep = S["PhaseStep"];
export type GateCheck = S["GateCheck"];
export type ArtifactView = S["ArtifactView"];
export type AuditView = S["AuditView"];
export type TemplateView = S["TemplateView"];
export type Phase = MissionSummary["phase"];
export type OrderSummary = S["OrderSummary"];
export type OrderDetail = S["OrderDetail"];
export type SupplierInvoice = S["SupplierInvoice"];
export type CandidateView = S["CandidateView"];
export type ExplanationItem = S["Item"];
export type SourcingView = S["SourcingView"];
export type IntakeView = S["IntakeView"];
export type FieldView = S["FieldView"];
export type ContractsView = S["ContractsView"];
export type ContractView = S["ContractView"];
export type RuleResult = S["RuleResult"];
export type TimesheetsView = S["TimesheetsView"];
export type TimesheetView = S["TimesheetView"];
export type AnomalyView = S["AnomalyView"];
export type InvoicesView = S["InvoicesView"];
export type InvoiceView = S["InvoiceView"];
export type MatchLine = S["LineResult"];
export type CopilotAnswer = S["CopilotAnswer"];
export type Citation = S["Citation"];
export type ToolCallView = S["ToolCallView"];
