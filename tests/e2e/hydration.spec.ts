import { test, expect } from "@playwright/test";
for (const saved of [false, true]) {
  for (const route of ["/", "/programs/feconf", "/?view=saved"]) {
    test(`direct hydration ${route} existing saved=${saved}`, async ({ page }) => {
      const errors: string[] = [];
      page.on("console", msg => { if (msg.type() === "error") errors.push(msg.text()); });
      page.on("pageerror", error => errors.push(error.message));
      if (saved) await page.addInitScript(() => localStorage.setItem("dearby:saved:v1", JSON.stringify({ programs: ["feconf"], organizations: ["org-fedg"] })));
      await page.goto(route);
      const button = page.locator('article:has(a[href="/programs/feconf"]) button').first();
      const target = route.startsWith("/programs") ? page.getByRole("button", { name: /^프로그램 스크랩/ }) : button;
      if (route.includes("saved") && !saved) {
        await expect(page.locator(".program-card")).toHaveCount(0);
      } else {
        await expect(target).toBeEnabled();
        await expect(target).toHaveAttribute("aria-pressed", String(saved));
        await target.click();
        await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem("dearby:saved:v1")!).programs.includes("feconf"))).toBe(!saved);
      }
      expect(errors).toEqual([]);
    });
  }
}
