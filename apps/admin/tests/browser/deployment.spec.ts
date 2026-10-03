import { test, expect } from "@playwright/test";

// Synthetic sessions only; all Supabase traffic is intercepted before reaching a server.
for (const permission of [false, true, "error"] as const) {
  test(`deep link and reload with administrator permission ${permission}`, async ({ page }, testInfo) => {
    const user = { id: "00000000-0000-4000-8000-000000000001", email: "test@example.com" };
    const session = { access_token: "test-token", refresh_token: "test-refresh", expires_at: 4102444800, token_type: "bearer", user };
    await page.addInitScript((value) => {
      localStorage.setItem("sb-localhost-auth-token", JSON.stringify(value));
    }, session);
    const dataRequests: string[] = [];
    await page.route("http://localhost:54321/**", async (route) => {
      const path = new URL(route.request().url()).pathname;
      if (path === "/auth/v1/user") return route.fulfill({ json: user });
      if (path === "/rest/v1/rpc/is_catalog_admin") return route.fulfill({ status: permission === "error" ? 500 : 200, json: permission === true });
      dataRequests.push(path);
      await route.fulfill({ json: [], headers: { "content-range": "0-0/0" } });
    });
    await page.goto("/collection");
    await page.reload();
    if (permission === true) {
      await expect(page.getByRole("heading", { name: "활동 수집", exact: true })).toBeVisible();
      await expect(page.locator(".ant-spin-spinning")).toHaveCount(0);
      await page.screenshot({ path: testInfo.outputPath("collection.png"), fullPage: true });
      await page.getByRole("link", { name: "활동", exact: true }).click();
      await expect(page.getByRole("heading", { name: "활동 관리", exact: true })).toBeVisible();
      await page.getByRole("button", { name: "활동 만들기" }).click();
      await expect(page.getByLabel("활동 제목", { exact: true })).toBeVisible();
    } else {
      await expect(page.getByRole("button", { name: "관리자로 로그인" })).toBeVisible();
      await expect(page.getByRole("navigation", { name: "관리 메뉴" })).toHaveCount(0);
      expect(dataRequests).toEqual([]);
    }
  });
}
test("anonymous deep links do not request protected data", async ({ page }, testInfo) => {
  const requests: string[] = [];
  await page.route("http://localhost:54321/**", async (route) => {
    requests.push(route.request().url());
    await route.abort();
  });
  await page.setViewportSize({ width: 390, height: 844 });
  for (const path of ["/collection", "/activities", "/activities/new", "/audit", "/organizations", "/programs"]) {
    await page.goto(path);
    await expect(page.getByRole("button", { name: "관리자로 로그인" })).toBeVisible();
    await expect(page.getByRole("navigation", { name: "관리 메뉴" })).toHaveCount(0);
  }
  expect(requests).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath("login-mobile.png"), fullPage: true });
});
