import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests/e2e", testMatch: ["hydration.spec.ts", "conference.spec.ts", "clubs.spec.ts"], workers: 1,
  outputDir: "test-results/dev",
  use: { baseURL: "http://127.0.0.1:3211", trace: "retain-on-failure" },
  projects: [{ name: "chromium", use: { browserName: "chromium" } }, { name: "webkit", use: { browserName: "webkit" } }],
  webServer: { command: "npm run dev -- --hostname 127.0.0.1 --port 3211", url: "http://127.0.0.1:3211", reuseExistingServer: false },
});
