import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.request.post("/api/auth/demo-login", { data: { persona: "admin" } });
  await page.request.post("/api/admin/demo/reset");
  await page.request.post("/api/auth/logout");
});

test("agency adds a worker, proposes them on a client order and withdraws", async ({ page }) => {
  const lastName = `Martin${Date.now().toString(36)}`;
  const fullName = `Zoé ${lastName}`;
  await page.goto("/login");
  await page.getByTestId("persona-nadia").click();
  await expect(page.getByTestId("supplier-todo")).toContainText(/Propose candidates|Strengthen your shortlist/);

  await page.getByRole("link", { name: "My workers" }).first().click();
  await page.getByTestId("add-worker").click();
  await page.getByLabel("First name").fill("Zoé");
  await page.getByLabel("Last name").fill(lastName);
  await page.getByLabel("Email").fill("zoe.martin@mail.example");
  await page.getByLabel("Phone").fill("06 11 22 33 44");
  await page.getByLabel("City").selectOption("Roubaix");
  await page.getByLabel("Years of experience").fill("5");
  await page.getByLabel("Available from").fill("2026-09-01");
  await page.getByLabel("Skills (comma separated)").fill("préparation de commandes, scanner, emballage");
  await page.getByTestId("save-worker").click();
  await expect(page.getByTestId("worker-pool")).toContainText(fullName);

  await page.goto("/app");
  await page.getByTestId("supplier-order").filter({ hasText: "Préparateurs" }).click();
  await expect(page.getByTestId("tab-proposals")).toHaveAttribute("data-state", "active");
  const option = page.getByTestId("propose-worker").locator("option", { hasText: fullName });
  await page.getByTestId("propose-worker").selectOption((await option.getAttribute("value")) ?? "");
  await page.getByTestId("propose-submit").click();
  const card = page.getByTestId("my-proposal").filter({ hasText: fullName });
  await expect(card).toContainText("Rank");

  await card.getByRole("button", { name: "Withdraw" }).click();
  await expect(page.getByTestId("my-proposal").filter({ hasText: fullName })).toHaveCount(0);
});
