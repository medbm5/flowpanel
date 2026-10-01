import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  // Fresh demo data so the run is repeatable (admin-only endpoint).
  const admin = await page.request.post("/api/auth/demo-login", { data: { persona: "admin" } });
  expect(admin.ok()).toBeTruthy();
  const reset = await page.request.post("/api/admin/demo/reset");
  expect(reset.ok()).toBeTruthy();
  await page.request.post("/api/auth/logout");
});

test("walks one mission from Intake to Closed", async ({ page }) => {
  test.setTimeout(120_000);
  await page.goto("/login");
  await page.getByTestId("persona-claire").click();
  await expect(page.getByTestId("mission-list")).toBeVisible();

  // Create from a template → opens at Intake
  await page.getByTestId("new-mission").first().click();
  await page.getByTestId("template-office-paris").click();
  await page.getByTestId("create-mission").click();
  await expect(page.getByTestId("workspace-INTAKE")).toBeVisible();

  // A locked phase explains which phase to finalize first
  await page.getByTestId("rail-CLOSED").click({ force: true });
  await expect(page.getByText("Finalize Intake first.")).toBeVisible();

  const finalize = page.getByTestId("finalize");

  // Intake: AI extraction, one low-confidence field to confirm
  await expect(finalize).toBeDisabled();
  await page.getByRole("button", { name: "Extract order with AI" }).click();
  const flagged = page.locator('[data-needs-review="true"]');
  await expect(flagged).toHaveCount(1);
  await flagged.getByRole("button", { name: "Confirm" }).click();
  await expect(flagged).toHaveCount(0);
  await expect(finalize).toBeEnabled();
  await finalize.click();

  // Sourcing: publish, select the top-ranked candidate
  await expect(page.getByTestId("workspace-SOURCING")).toBeVisible();
  await page.getByRole("button", { name: "Publish to panel" }).click();
  await expect(page.getByTestId("candidate-card").first()).toBeVisible();
  await expect(page.getByTestId("excluded")).toBeVisible();
  await page.getByTestId("candidate-card").first().getByRole("button", { name: "Select" }).click();
  await expect(finalize).toBeEnabled();
  await finalize.click();

  // Contracts: generate, fix the blocking issue, sign
  await expect(page.getByTestId("workspace-CONTRACTS")).toBeVisible();
  await page.getByRole("button", { name: "Generate contracts" }).click();
  await expect(page.getByText("Blocking issue", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Fix" }).click();
  await expect(page.getByText("Ready to sign", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Sign all (e-signature)" }).click();
  await expect(page.getByText("Signed", { exact: true })).toBeVisible();
  await finalize.click();

  // Timesheets: checks, anomaly with AI explanation, approve overtime, approve
  await expect(page.getByTestId("workspace-TIMESHEETS")).toBeVisible();
  await page.getByRole("button", { name: "Run checks" }).click();
  const anomaly = page.getByTestId("anomaly");
  await expect(anomaly).toContainText("41 h worked vs 35 h contracted");
  await expect(page.locator("[data-flagged]").first()).toBeVisible();
  await anomaly.getByRole("button", { name: "Approve overtime" }).click();
  await page.getByRole("button", { name: "Approve timesheets" }).click();
  await expect(finalize).toBeEnabled();
  await finalize.click();

  // Invoice: receive, mismatch, AI-drafted credit note request, approve
  await expect(page.getByTestId("workspace-INVOICE")).toBeVisible();
  await page.getByRole("button", { name: "Receive invoices" }).click();
  await expect(page.getByText("Hours mismatch", { exact: true })).toBeVisible();
  await expect(page.getByTestId("credit-note-message")).toContainText("avoir");
  await page.getByRole("button", { name: "Send credit note request" }).click();
  await expect(page.getByText(/Credit note AV-/)).toBeVisible();
  await page.getByRole("button", { name: "Approve invoices" }).click();
  await expect(finalize).toBeEnabled();
  await finalize.click();

  // Closed with a summary
  await expect(page.getByTestId("workspace-CLOSED")).toBeVisible();
  await expect(page.getByTestId("summary")).toContainText("Workers placed");
  await expect(page.getByTestId("audit-trail")).toContainText("finalized");
});
