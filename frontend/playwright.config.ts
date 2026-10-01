import { defineConfig, devices } from "@playwright/test";

/**
 * Smoke tests against a running backend (mock AI profile) on BACKEND_URL (default http://localhost:8080).
 * The frontend is started by Playwright from the production build (`npm run build` first).
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["list"]] : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: "npm run start -- --port 3000",
        url: "http://localhost:3000/login",
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
});
