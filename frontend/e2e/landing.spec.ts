import { expect, test } from "@playwright/test";

test("landing page: FAQ opens and the demo CTA leads to login", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("From the first email to the last invoice, one workflow.");

  const faq = page.getByTestId("faq");
  await faq.scrollIntoViewIfNeeded();
  const question = faq.getByRole("button", { name: "Can the AI reject a candidate?" });
  await question.click();
  await expect(question).toHaveAttribute("aria-expanded", "true");
  await expect(faq.getByText(/Candidates are excluded only by deterministic rules/)).toBeVisible();

  await page.getByTestId("hero-cta").click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("heading", { name: "Choose a demo persona" })).toBeVisible();
});

for (const width of [360, 768, 1440]) {
  for (const scheme of ["light", "dark"] as const) {
    test(`renders without horizontal overflow at ${width}px (${scheme})`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: scheme });
      await page.setViewportSize({ width, height: 900 });
      await page.goto("/");
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      expect(overflow).toBeLessThanOrEqual(0);
      await page.screenshot({ path: `test-results/landing-${width}-${scheme}.png`, fullPage: width !== 1440 });
    });
  }
}
