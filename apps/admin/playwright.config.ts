import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "tests/browser",
  fullyParallel: false,
  workers: 1,
  timeout: 60000,
  use: {
    actionTimeout: 10000,
    baseURL: "http://127.0.0.1:5173",
    viewport: { width: 1440, height: 1000 },
    timezoneId: "Asia/Seoul",
    locale: "ko-KR",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  reporter: "list",
});
