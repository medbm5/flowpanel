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
