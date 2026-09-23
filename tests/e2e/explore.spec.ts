import AxeBuilder from "@axe-core/playwright";
import { test, expect } from "@playwright/test";
import path from "node:path";
test("explore, detail, separate saved entities and reload restoration", async ({
  page,
}, info) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("/");
  await expect(page.locator(".program-card")).toHaveCount(18);
  await expect(
    page.getByText("2026.09.24 기준", { exact: false }),
  ).toBeVisible();
  await page
    .locator("img")
    .evaluateAll((imgs) =>
      Promise.all(imgs.map((img) => { (img as HTMLImageElement).loading = "eager"; return (img as HTMLImageElement).decode(); })),
    );
  await page.screenshot({
    path: path.join("test-results", `${info.project.name}-explore.png`),
    fullPage: true,
  });
  await page.screenshot({
    path: path.join(
      "test-results",
      `${info.project.name}-${page.url().includes("/programs/") ? "detail" : "explore"}-viewport.png`,
    ),
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  await page
    .locator(".program-card")
    .first()
    .getByRole("button", { name: "프로그램 스크랩", exact: true })
    .click();
  await page.locator(".card-title").first().click();
  await expect(page).toHaveURL(/\/programs\//);
  await expect(
    page.getByRole("heading", { name: "회차별 참가 정보", exact: false }),
  ).toBeVisible();
  await page.getByRole("button", { name: "조직 스크랩", exact: true }).click();
  await page
    .locator("img")
    .evaluateAll((imgs) =>
      Promise.all(imgs.map((img) => { (img as HTMLImageElement).loading = "eager"; return (img as HTMLImageElement).decode(); })),
    );
  await page.screenshot({
    path: path.join("test-results", `${info.project.name}-detail.png`),
    fullPage: true,
  });
  await page.screenshot({
    path: path.join(
      "test-results",
      `${info.project.name}-${page.url().includes("/programs/") ? "detail" : "explore"}-viewport.png`,
    ),
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  await page.reload();
  await expect(
    page.getByRole("button", { name: "조직 스크랩 해제" }),
  ).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("link", { name: "스크랩", exact: true }).click();
  await expect(page.locator(".program-card")).toHaveCount(1);
  await page.getByRole("button", { name: "조직 1", exact: true }).click();
  await expect(page.locator(".organization-list article")).toHaveCount(1);
  await page.getByRole("button", { name: "조직 스크랩 해제" }).click();
  await expect(page.getByText("아직 스크랩한 조직이 없어요")).toBeVisible();
  await page.getByRole("button", { name: "프로그램 1", exact: true }).click();
  await page.getByRole("button", { name: "프로그램 스크랩 해제" }).click();
  await expect(page.locator(".program-card")).toHaveCount(0);
  expect(errors).toEqual([]);
});
test("filter subset suggestions apply and undo, search and recruitment", async ({
  page,
}, info) => {
  await page.goto("/");
  await page.getByRole("button", { name: "백엔드", exact: true }).click();
  await page.getByRole("button", { name: /경험·상세 필터/ }).click();
  await page.getByLabel("제작 | 앱 · 팀협업", { exact: true }).check();
  await page.getByRole("button", { name: "결과 보기" }).click();
  await expect(
    page.getByText("모든 조건에 맞는 프로그램이 없어요"),
  ).toBeVisible();
  await page
    .locator("img")
    .evaluateAll((imgs) =>
      Promise.all(imgs.map((img) => { (img as HTMLImageElement).loading = "eager"; return (img as HTMLImageElement).decode(); })),
    );
  await page.screenshot({
    path: path.join("test-results", `${info.project.name}-empty.png`),
    fullPage: true,
  });
  await page.locator(".suggestions button").first().click();
  await expect(page.locator(".program-card").first()).toBeVisible();
  await expect(page.getByText("해제된 조건:", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: "되돌리기" }).click();
  await expect(page.locator(".program-card")).toHaveCount(0);
  await page.getByRole("button", { name: "필터 초기화" }).first().click();
  await expect(page.locator(".program-card")).toHaveCount(18);
  await page.getByRole("button", { name: /경험·상세 필터/ }).click();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page.getByRole("textbox", { name: "프로그램 검색" }).fill("FEConf");
  await page.getByRole("button", { name: "검색", exact: true }).click();
  await expect(page.locator(".program-card")).toHaveCount(1);
  await page.getByRole("link", { name: "모집·등록 중", exact: true }).click();
  await expect(page.locator(".program-card")).toHaveCount(3);
  await page.getByRole("button", { name: "iOS", exact: true }).click();
  await expect(page.locator(".program-card")).toHaveCount(1);
  await page.getByRole("button", { name: "필터 초기화", exact: true }).click();
  await expect(page).toHaveURL("http://127.0.0.1:3210/");
  await expect(page.locator(".program-card")).toHaveCount(18);
  await expect(
    page.getByRole("heading", { name: "다음 컨퍼런스를 만나보세요", exact: true }),
  ).toBeVisible();
});
test("corrupt storage and write failure are visible, keyboard focus is reachable", async ({
  page,
}) => {
  await page.addInitScript(() => {
    localStorage.setItem("dearby:saved:v1", "invalid");
    const original = Storage.prototype.setItem;
    let blocked = true;
    Storage.prototype.setItem = function (key, value) {
      if (blocked) {
        blocked = false;
        throw new Error("blocked");
      }
      return original.call(this, key, value);
    };
  });
  await page.goto("/");
  await expect(page.getByRole("status")).toContainText("불러오지 못했습니다");
  await expect(
    page
      .locator(".program-card")
      .first()
      .getByRole("button", { name: "프로그램 스크랩", exact: true }),
  ).toBeDisabled();
  expect(
    await page.evaluate(() => localStorage.getItem("dearby:saved:v1")),
  ).toBe("invalid");
  await page.getByRole("button", { name: "손상된 스크랩 초기화" }).click();
  await page
    .locator(".program-card")
    .first()
    .getByRole("button", { name: "프로그램 스크랩", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("브라우저 저장에 실패");
  await page.getByRole("button", { name: "다시 시도", exact: true }).click();
  await expect(page.getByRole("status")).toContainText(
    "현재 스크랩을 브라우저에 저장했습니다",
  );
  await page.keyboard.press("Tab");
  expect(await page.evaluate(() => document.activeElement?.tagName)).not.toBe(
    "BODY",
  );
});

test("accessible explore, modal and detail; menu toggles and focus returns", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.locator("body")).toBeVisible();
  const audit = async () => {
    const result = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
      .analyze();
    expect(result.violations).toEqual([]);
  };
  await audit();
  await page
    .getByRole("button", { name: "탐색 메뉴 접기 또는 펼치기" })
    .click();
  await expect(
    page.getByRole("button", { name: "탐색 메뉴 접기 또는 펼치기" }),
  ).toHaveAttribute("aria-expanded", "false");
  await page
    .getByRole("button", { name: "탐색 메뉴 접기 또는 펼치기" })
    .click();
  const trigger = page.getByRole("button", { name: /경험·상세 필터/ });
  await trigger.click();
  await audit();
  await page.keyboard.press("Escape");
  await expect(trigger).toBeFocused();
  await page.goto("/programs/kakao");
  await audit();
});

test("filters survive details and browser back; saved organization tab has relevant controls", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("button", { name: "백엔드", exact: true }).click();
  const count = await page.locator(".program-card").count();
  await expect(page).toHaveURL(/roles=/);
  await page.locator(".card-title").first().click();
  await expect(page).toHaveURL(/\/programs\//);
  await page.goBack();
  await expect(page).toHaveURL(/roles=/);
  await expect(
    page.getByRole("button", { name: "백엔드", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator(".program-card")).toHaveCount(count);
  await page.reload();
  await expect(page.locator(".program-card")).toHaveCount(count);
  await page.getByRole("link", { name: "스크랩", exact: true }).click();
  await page.getByRole("button", { name: "조직 0", exact: true }).click();
  await expect(page.locator(".chip-row")).toHaveCount(0);
  await expect(page.locator(".result-count")).toHaveText("조직 0개");
});
test("another tab clear is reflected and denied storage can be retried", async ({
  page,
  context,
}) => {
  await page.goto("/");
  await page
    .locator(".program-card")
    .first()
    .getByRole("button", { name: "프로그램 스크랩", exact: true })
    .click();
  const other = await context.newPage();
  await other.goto("/");
  await other.evaluate(() => localStorage.clear());
  await expect(
    page
      .locator(".program-card")
      .first()
      .getByRole("button", { name: "프로그램 스크랩", exact: true }),
  ).toHaveAttribute("aria-pressed", "false");
  await other.close();
  await page.evaluate(() => {
    const original = Storage.prototype.getItem;
    let blocked = true;
    Storage.prototype.getItem = function (key) {
      if (blocked) {
        blocked = false;
        throw new Error("blocked");
      }
      return original.call(this, key);
    };
    window.dispatchEvent(new StorageEvent("storage", { key: null }));
  });
  await expect(page.getByRole("status")).toContainText("접근할 수 없습니다");
  await expect(
    page
      .locator(".program-card")
      .first()
      .getByRole("button", { name: "프로그램 스크랩", exact: true }),
  ).toBeDisabled();
  await page.getByRole("button", { name: "다시 시도", exact: true }).click();
  await expect(
    page
      .locator(".program-card")
      .first()
      .getByRole("button", { name: "프로그램 스크랩", exact: true }),
  ).toBeEnabled();
  await page.emulateMedia({ reducedMotion: "reduce" });
  await expect(page.locator(".cover-link img").first()).toHaveCSS(
    "transition-duration",
    "0s",
  );
});

test("card and detail use the latest verified round without claiming unrestricted eligibility", async ({ page }) => {
  await page.goto("/?q=if(kakao)");
  const card = page.locator('.program-card[data-notice-id="kakao-2026"]');
  await expect(card).toContainText("참가 신청 중");
  await expect(card).toContainText("2026.10.13 – 2026.10.14");
  await expect(card).toContainText("무료");
  await expect(card).toContainText("2026.09.28");
  await card.locator(".card-title").click();
  const notice = page.locator('.notice[data-notice-id="kakao-2026"]');
  await expect(notice).toContainText("만 18세 이상");
  await expect(notice).toContainText("낮 12시");
  await expect(page.locator('.notice[data-notice-id="kakao-2025"]')).toContainText("행사 종료");
  await expect(page.locator('.notice[data-notice-id="kakao-2025"]')).toContainText("2025.09.23");
});
