import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import path from "node:path";

test("official company logos load across cards, details and saved organizations", async ({ page }, info) => {
  const errors: string[] = [];
  page.on("pageerror", e => errors.push(e.message));
  page.on("console", m => { if (m.type() === "error") errors.push(m.text()); });
  await page.goto("/");
  for (const id of ["woowacon", "dan", "kakao", "toss", "techverse", "aws", "ktcloud", "saif"]) {
    const logo = page.locator(`.program-card:has(a[href="/programs/${id}"]) .avatar img`);
    await logo.scrollIntoViewIfNeeded();
    await expect(logo).toHaveAttribute("alt", "");
    await expect.poll(() => logo.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBe(true);
  }
  const community = page.locator('.program-card:has(a[href="/programs/gdgandroid"]) .avatar');
  await expect(community).toHaveText("G");
  await expect(community.locator("img")).toHaveCount(0);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: path.join("test-results", `${info.project.name}-logos-cards.png`), fullPage: true });
  await page.goto("/programs/woowacon");
  await expect.poll(() => page.locator(".channel-row .avatar img").evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBe(true);
  await page.getByRole("button", { name: "조직 스크랩", exact: true }).click();
  await page.getByRole("button", { name: "프로그램 스크랩", exact: true }).click();
  await page.screenshot({ path: path.join("test-results", `${info.project.name}-logos-detail.png`), fullPage: true });
  await page.getByRole("link", { name: "스크랩", exact: true }).click();
  await page.getByRole("button", { name: "조직 1", exact: true }).click();
  await page.reload();
  await expect(page.locator(".organization-list .avatar img")).toHaveAttribute("src", new URL("/organizations/woowa.svg", page.url()).href);
  await expect(page.getByRole("button", { name: "조직 스크랩 해제" })).toHaveAttribute("aria-pressed", "true");
  await page.screenshot({ path: path.join("test-results", `${info.project.name}-logos-saved.png`), fullPage: true });
  expect((await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze()).violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole("button", { name: "조직 스크랩 해제" }).click();
  await page.reload();
  await expect(page.getByText("아직 스크랩한 조직이 없어요")).toBeVisible();
  expect(errors).toEqual([]);
});

test("broken company logo falls back to initial without blocking saved actions", async ({ page }) => {
  await page.route("**/organizations/kakao.svg", route => route.fulfill({ status: 404, body: "missing" }));
  await page.goto("/programs/kakao");
  await expect(page.locator(".channel-row .avatar")).toHaveText("K");
  await expect(page.locator(".channel-row .avatar img")).toHaveCount(0);
  await expect(page.locator(".channel-row .avatar")).toHaveAttribute("aria-hidden", "true");
  await page.getByRole("button", { name: "조직 스크랩", exact: true }).click();
  await page.reload();
  await expect(page.getByRole("button", { name: "조직 스크랩 해제" })).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator(".channel-row .avatar")).toHaveText("K");
});
