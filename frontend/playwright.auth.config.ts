import { defineConfig, devices } from "@playwright/test";

const port = process.env.PLAYWRIGHT_AUTH_PORT ?? "31929";
const baseURL = `http://localhost:${port}`;

export default defineConfig({
  testDir: "./e2e/live",
  workers: 1,
  timeout: 60_000,
  webServer: {
    command: `node node_modules/next/dist/bin/next dev --hostname localhost --port ${port}`,
    url: baseURL,
    timeout: 120_000,
  },
  use: { baseURL, trace: "off", video: "off", screenshot: "off", ...devices["Desktop Chrome"] },
});
