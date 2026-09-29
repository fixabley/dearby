import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { cardId, secondCardId, revokedId, activityId } from "../fixtures";

test("discovery, detail, external application and responsive layout", async ({
  page,
}, testInfo) => {
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "모집 중인 활동" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "커넥트 IT 컨퍼런스 (테스트)" }),
  ).toBeVisible();
  await expect(page.locator("body")).not.toContainText("로그인하기");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.screenshot({
    path: testInfo.outputPath("discovery.png"),
    fullPage: true,
  });
  await page.getByRole("link", { name: /커넥트 IT 컨퍼런스/ }).click();
  await expect(page).toHaveURL(`/activities/${activityId}`);
  await expect(
    page.getByRole("link", { name: "공식 사이트에서 신청하기" }),
  ).toHaveAttribute("href", "https://example.test/apply");
  await expect(
    page.getByText("웹에서는 개인 캘린더의 겹치는 시간을 확인할 수 없어요."),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "겹치는 시간 확인하기" }),
  ).toHaveCount(0);
  await page.screenshot({
    path: testInfo.outputPath("detail.png"),
    fullPage: true,
  });
});

test("save round trip, persistent cookie, independent browser, remove and explicit reset", async ({
  page,
  context,
  browser,
}, testInfo) => {
  await page.goto(`/cards/${cardId}`);
  await expect(
    page.getByRole("heading", { name: "테스트 지민" }),
  ).toBeVisible();
  await expect(page.getByRole("heading", { name: "활동 이력" })).toBeVisible();
  expect(await context.cookies()).toEqual([]);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.screenshot({
    path: testInfo.outputPath("public-card.png"),
    fullPage: true,
  });
  const response = page.waitForResponse(
    (r) =>
      r.url().includes(`/api/guest/cards/${cardId}`) &&
      r.request().method() === "PUT",
  );
  await page.getByRole("button", { name: "명함 저장", exact: true }).click();
  expect(await (await response).json()).toEqual({ cardId, status: "saved" });
  await expect(page.getByRole("status")).toHaveText("명함을 저장했어요.");
  const cookies = await context.cookies();
  const cookie = cookies.find((cookie) => cookie.name === "dearby_guest_dev")!;
  expect(cookie.httpOnly).toBe(true);
  expect(cookie.sameSite).toBe("Lax");
  expect(cookie.value).toHaveLength(43);
  await page.goto("/saved");
  await expect(
    page.getByRole("heading", { name: "테스트 지민" }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "테스트 지민" }),
  ).toBeVisible();
  expect(
    (await context.cookies()).find((c) => c.name === cookie.name)?.value,
  ).toBe(cookie.value);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.screenshot({
    path: testInfo.outputPath("saved.png"),
    fullPage: true,
  });
  const independent = await browser.newContext();
  const other = await independent.newPage();
  await other.goto("http://localhost:3210/saved");
  await expect(
    other.getByRole("heading", { name: "아직 저장한 명함이 없어요" }),
  ).toBeVisible();
  expect(await independent.cookies()).toEqual([]);
  await independent.close();
  await page.getByRole("button", { name: "테스트 지민 저장 삭제" }).click();
  await expect(
    page.getByRole("heading", { name: "아직 저장한 명함이 없어요" }),
  ).toBeVisible();
  await page.goto(`/cards/${cardId}`);
  await page.getByRole("button", { name: "명함 저장", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("명함을 저장했어요.");
  await page.goto("/saved");
  page.once("dialog", (dialog) => dialog.accept());
  await page
    .getByRole("button", { name: "이 브라우저의 저장 세션 삭제" })
    .click();
  await expect(
    page.getByRole("heading", { name: "아직 저장한 명함이 없어요" }),
  ).toBeVisible();
  expect(await context.cookies()).toEqual([]);
});

test("first saves in two tabs share one cookie session", async ({
  page,
  context,
}) => {
  const other = await context.newPage();
  await page.goto(`/cards/${cardId}`);
  await other.goto(`/cards/${secondCardId}`);
  await Promise.all([
    page.getByRole("button", { name: "명함 저장", exact: true }).click(),
    other.getByRole("button", { name: "명함 저장", exact: true }).click(),
  ]);
  await expect(page.getByRole("status")).toHaveText("명함을 저장했어요.");
  await expect(other.getByRole("status")).toHaveText("명함을 저장했어요.");
  await page.goto("/saved");
  await expect(
    page.getByRole("heading", { name: "테스트 지민" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "테스트 서연" }),
  ).toBeVisible();
});

test("revocation, save failure, empty and server failure remain distinct", async ({
  page,
}, testInfo) => {
  await page.goto(`/cards/${revokedId}`);
  await expect(
    page.getByRole("heading", { name: "명함을 볼 수 없어요" }),
  ).toBeVisible();
  await page.goto(`/cards/${cardId}`);
  await page.route(`**/api/guest/cards/${cardId}`, (route) =>
    route.fulfill({
      status: 503,
      json: { error: { code: "GUEST_UNAVAILABLE" } },
    }),
  );
  await page.getByRole("button", { name: "명함 저장", exact: true }).click();
  await expect(page.getByRole("main").getByRole("alert")).toContainText(
    "서버에 연결하지 못했어요",
  );
  await expect(
    page.getByText("명함을 저장했어요.", { exact: true }),
  ).toHaveCount(0);
  await page.route("**/api/catalog", (route) =>
    route.fulfill({ status: 502, json: {} }),
  );
  await page.goto("/");
  await expect(page.getByRole("main").getByRole("alert")).toContainText(
    "서버에 연결하지 못했어요",
  );
  await expect(
    page.getByRole("heading", { name: "지금은 모집 중인 활동이 없어요" }),
  ).toHaveCount(0);
  await page.screenshot({
    path: testInfo.outputPath("server-error.png"),
    fullPage: true,
  });
  await page.route("**/api/catalog", (route) =>
    route.fulfill({
      status: 200,
      json: {
        generatedAt: new Date().toISOString(),
        activities: [],
        organizations: [],
        programs: [],
      },
    }),
  );
  await page.getByRole("button", { name: "다시 시도" }).click();
  await expect(
    page.getByRole("heading", { name: "지금은 모집 중인 활동이 없어요" }),
  ).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("empty.png"),
    fullPage: true,
  });
});

test("deleted cookie loses access; invalid session never silently replaces identity", async ({
  page,
  context,
}) => {
  await context.addCookies([
    {
      name: "dearby_guest_dev",
      value: "x".repeat(43),
      domain: "localhost",
      path: "/",
      httpOnly: true,
      sameSite: "Lax",
    },
  ]);
  await page.goto("/saved");
  await expect(
    page.getByRole("heading", { name: "저장 세션을 사용할 수 없어요" }),
  ).toBeVisible();
  await page.goto(`/cards/${cardId}`);
  await page.getByRole("button", { name: "명함 저장", exact: true }).click();
  await expect(page.getByRole("main").getByRole("alert")).toContainText(
    "저장 세션을 사용할 수 없어요",
  );
  expect((await context.cookies())[0].value).toBe("x".repeat(43));
  await context.clearCookies();
  await page.goto("/saved");
  await expect(
    page.getByRole("heading", { name: "아직 저장한 명함이 없어요" }),
  ).toBeVisible();
  await expect(page.getByText(/쿠키 삭제·브라우저 보관 제한/)).toBeVisible();
});

test("320px and enlarged text remain within viewport", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 740 });
  await page.goto(`/cards/${cardId}`);
  await expect(
    page.getByRole("heading", { name: "테스트 지민" }),
  ).toBeVisible();
  await page.addStyleTag({ content: "body { font-size: 24px !important; }" });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await expect(
    page.getByRole("button", { name: "명함 저장", exact: true }),
  ).toBeVisible();
});
