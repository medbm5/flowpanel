import { expect, test } from "@playwright/test";

test("editing the hourly rate accepts French formats and shows errors inline", async ({ page }) => {
  await page.goto("/login");
  await page.getByTestId("persona-claire").click();
  await page.getByTestId("new-mission").first().click();
  await page.getByTestId("template-pickers-roubaix").click();
  await page.getByTestId("create-mission").click();
  await page.getByRole("button", { name: "Extract order with AI" }).click();

  const rate = page.getByTestId("field-hourlyRate");
  await rate.getByRole("button", { name: "Edit Hourly rate (EUR)" }).click();
  const input = rate.getByRole("textbox", { name: "New value for Hourly rate (EUR)" });

  await input.fill("douze");
  await input.press("Enter");
  await expect(rate.getByRole("alert")).toContainText("must be a number");

  await input.fill("12,50 €");
  await input.press("Enter");
  await expect(rate).toContainText("12.50");
  await expect(rate).toHaveAttribute("data-needs-review", "false");
});
