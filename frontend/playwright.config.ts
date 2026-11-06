import { defineConfig, devices } from "@playwright/test";

const port = process.env.PLAYWRIGHT_PORT ?? "3000";
const localUrl = "http://127.0.0.1:" + port;

export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  webServer: {
    command: "node node_modules/next/dist/bin/next dev --hostname 127.0.0.1 --port " + port,
    url: localUrl,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  expect: {
    timeout: 10_000,
  },
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? localUrl,
    trace: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
