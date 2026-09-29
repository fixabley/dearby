import { defineConfig, devices } from "@playwright/test";
export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: false,
  workers: 1,
  use: { baseURL: "http://localhost:3210", trace: "retain-on-failure" },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] } },
    {
      name: "mobile",
      use: { ...devices["iPhone 13"], defaultBrowserType: "chromium" },
    },
  ],
  webServer: [
    {
      command: "npx tsx tests/fixture-api.ts",
      port: 4319,
      reuseExistingServer: false,
    },
    {
      command: "npm run dev -- --hostname localhost --port 3210",
      url: "http://localhost:3210",
      reuseExistingServer: false,
      env: {
        DEARBY_API_ORIGIN: "http://127.0.0.1:4319",
        GUEST_PROXY_SECRET: "test-proxy-secret-with-more-than-32-characters",
      },
    },
  ],
});
