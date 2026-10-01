import { expect, test } from "@playwright/test";

test("copilot answers a policy question with a verified citation", async ({ page }) => {
  await page.goto("/login");
  await page.getByTestId("persona-claire").click();
  await page.getByTestId("open-copilot").click();
  await page.getByTestId("copilot-input").fill("What is the night work bonus?");
  await page.getByTestId("copilot-input").press("Enter");
  const answer = page.getByTestId("copilot-answer").last();
  await expect(answer).toContainText("25 %");
  await answer.getByTestId("citation-chip").first().click();
  await expect(page.getByRole("dialog").last()).toContainText("Night work policy");
  await page.getByRole("dialog").last().getByRole("button", { name: "Close" }).click();
  await answer.getByTestId("copilot-steps").click();
  await expect(answer).toContainText("searchDocuments");
});

test("admin dashboard shows real numbers from ai_call", async ({ page }) => {
  await page.goto("/login");
  await page.getByTestId("persona-admin").click();
  await expect(page).toHaveURL(/\/app\/admin/);
  const tiles = page.getByTestId("metric-tiles");
  await expect(tiles).toContainText("AI calls");
  const calls = await tiles.locator("dd").first().innerText();
  expect(Number(calls.replace(/[^\d]/g, ""))).toBeGreaterThan(0);
  await expect(page.getByText("intake.extract").first()).toBeVisible();
  await page.getByTestId("audit-kind").selectOption("AI");
  await expect(page.getByTestId("audit-table")).toContainText("AI");
  await page.screenshot({ path: "test-results/admin-dashboard.png", fullPage: true });
});
