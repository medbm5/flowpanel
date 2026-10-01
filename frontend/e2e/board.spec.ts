import { expect, test } from "@playwright/test";

test("board reflects backend state and a new mission appears without a reload", async ({ page }) => {
  await page.goto("/login");
  await page.getByTestId("persona-claire").click();
  await expect(page.getByTestId("mission-list")).toBeVisible();
  const before = await page.getByTestId("mission-row").count();
  expect(before).toBeGreaterThanOrEqual(2);

  await page.getByRole("tab", { name: "Needs review" }).click();
  await page.getByRole("tab", { name: "All" }).click();

  await page.getByTestId("new-mission").first().click();
  await page.getByTestId("template-office-paris").click();
  await page.getByTestId("create-mission").click();
  await expect(page).toHaveURL(/\/app\/missions\/\d+/);
  await page.goBack();
  await expect(page.getByTestId("mission-row")).toHaveCount(before + 1);
});

test("supplier persona sees the supplier board", async ({ page }) => {
  await page.goto("/login");
  await page.getByTestId("persona-nadia").click();
  await expect(page.getByRole("heading", { name: "InterSud Intérim", level: 1 })).toBeVisible();
  await expect(page.getByTestId("supplier-kpis")).toBeVisible();
  await expect(page.getByTestId("supplier-orders")).toBeVisible();
});
