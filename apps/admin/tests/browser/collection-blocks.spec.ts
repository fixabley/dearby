import { test, expect } from "@playwright/test";

// Synthetic session and intercepted HTTP only.
test("collection jobs label subscription and source blocks with next steps", async ({
  page,
}, testInfo) => {
  const user = {
    id: "00000000-0000-4000-8000-000000000001",
    email: "test@example.com",
  };
  await page.addInitScript(
    (value) => {
      localStorage.setItem("sb-localhost-auth-token", JSON.stringify(value));
    },
    {
      access_token: "test-token",
      refresh_token: "test-refresh",
      expires_at: 4102444800,
      token_type: "bearer",
      user,
    },
  );
  const job = (id: string, program_name: string, error: string) => ({
    id,
    program_name,
    error,
    status: "blocked",
    scheduled_day: "2026-10-06",
    attempts: 1,
    finished_at: null,
    stats: {},
    usage: {},
    run_history: [],
  });
  await page.route("http://localhost:54321/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/auth/v1/user") return route.fulfill({ json: user });
    if (path === "/rest/v1/rpc/is_catalog_admin")
      return route.fulfill({ json: true });
    if (path === "/rest/v1/catalog_collection_jobs")
      return route.fulfill({
        json: [
          job(
            "00000000-0000-4000-8000-0000000000a1",
            "구독 프로그램",
            "BLOCKED: Codex subscription quota or login requires attention",
          ),
          job(
            "00000000-0000-4000-8000-0000000000a2",
            "원문 프로그램",
            "BLOCKED: Official page exceeds 3 MB",
          ),
        ],
        headers: { "content-range": "0-1/2" },
      });
    await route.fulfill({ json: [], headers: { "content-range": "0-0/0" } });
  });
  await page.goto("/collection");
  const row = (name: string) => page.getByRole("row").filter({ hasText: name });
  await expect(row("구독 프로그램")).toContainText("구독 차단 · 전체 일시정지");
  await expect(row("구독 프로그램")).toContainText(
    "모든 프로그램 수집이 멈춥니다",
  );
  await expect(row("원문 프로그램")).toContainText("원문 차단 · 이 프로그램만");
  await expect(row("원문 프로그램")).toContainText("호스트·크기");
  await expect(
    row("원문 프로그램").getByRole("button", { name: "원인 해소 후 재시도" }),
  ).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("collection-blocks-1440.png"),
    fullPage: true,
    animations: "disabled",
  });
});
