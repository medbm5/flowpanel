import { expect, test } from "@playwright/test";

test("protected app redirects to login, persona login lands on /app", async ({ page }) => {
  await page.goto("/app");
  await expect(page).toHaveURL(/\/login\?next=%2Fapp/);

  await page.getByTestId("persona-claire").click();
  await expect(page).toHaveURL(/\/app$/);
  await expect(page.getByTestId("tenant-name")).toHaveText("LogiNord");
});
