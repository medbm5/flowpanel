import createClient from "openapi-fetch";
import type { paths } from "./schema";

/** Typed client for the backend, always through the same-origin /api rewrite. */
export const api = createClient<paths>({
  baseUrl: typeof window === "undefined" ? "/api" : `${window.location.origin}/api`,
  credentials: "include",
  // Resolved at call time so tests can stub fetch.
  fetch: (request) => globalThis.fetch(request),
});

/** RFC 7807 problem returned by the backend. */
export type Problem = {
  title?: string;
  detail?: string;
  status?: number;
  code?: string;
  unmetChecks?: string[];
  [key: string]: unknown;
};

export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem;

  constructor(status: number, problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed (${status})`);
    this.status = status;
    this.problem = problem;
  }
}

/** Unwraps an openapi-fetch result: returns data or throws an ApiError with the ProblemDetail. */
export async function call<T>(
  promise: Promise<{ data?: T; error?: unknown; response: Response }>,
): Promise<T> {
  const { data, error, response } = await promise;
  if (!response.ok) {
    const problem = (error && typeof error === "object" ? error : { detail: String(error ?? "") }) as Problem;
    throw new ApiError(response.status, problem);
  }
  return data as T;
}

/** Human message for an error, including unmet gate checks when present. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const checks = error.problem.unmetChecks;
    return checks?.length ? `${error.message}: ${checks.join(", ")}` : error.message;
  }
  return error instanceof Error ? error.message : "Something went wrong";
}
