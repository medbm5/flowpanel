import { expect, test } from "@playwright/test";

test("admin sees LLM usage per user with a detail breakdown", async ({ page }) => {
  await page.goto("/login");
  await page.getByTestId("persona-admin").click();
  await page.getByRole("link", { name: "LLM usage per user" }).click();
  await expect(page).toHaveURL(/\/app\/admin\/usage/);
  await expect(page.getByTestId("usage-totals")).toContainText("Estimated cost");
  await expect(page.getByTestId("usage-users")).toContainText("Claire Dubois");

  await page.getByTestId("usage-row-claire").click();
  const detail = page.getByTestId("usage-detail");
  await expect(detail).toContainText("Claire Dubois");
  await expect(page.getByTestId("usage-by-feature")).toContainText("intake.extract");
  await expect(page.getByTestId("usage-recent")).toBeVisible();

  await page.getByTestId("window-7").click();
  await expect(page.getByTestId("window-7")).toHaveAttribute("aria-pressed", "true");
  await page.screenshot({ path: "test-results/usage-dashboard.png", fullPage: true });
});
