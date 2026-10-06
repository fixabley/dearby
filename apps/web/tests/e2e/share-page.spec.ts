import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import data from "../card-shares.json";

const [first, , , , revoked] = data.shares;

test("share link shows the card and chosen activities, saves in one step and moves focus", async ({
  page,
}) => {
  await page.goto(`/s/${first.id}`);
  await expect(
    page.getByRole("heading", { name: "테스트 지민" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "함께 공유된 활동" }),
  ).toBeVisible();
  for (const activity of first.activities)
    await expect(
      page.getByRole("link", { name: activity.title }),
    ).toBeVisible();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.getByRole("button", { name: "명함 저장" }).click();
  await expect(page.getByText("이 브라우저에만 저장됐어요.")).toBeVisible();
  await expect(
    page.getByRole("link", { name: "저장한 명함 보기" }),
  ).toBeFocused();
  await page.goto("/saved");
  await expect(page.getByText("테스트 지민")).toBeVisible();
});

test("revoked or malformed share links show the unavailable card screen", async ({
  page,
}) => {
  for (const path of [`/s/${revoked.id}`, "/s/not-a-uuid"]) {
    await page.goto(path);
    await expect(
      page.getByRole("heading", { name: "명함을 볼 수 없어요" }),
    ).toBeVisible();
  }
});

test("in-app browsers get the external browser notice without blocking save", async ({
  browser,
}) => {
  const context = await browser.newContext({
    userAgent:
      "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 KAKAOTALK 10.9.0",
  });
  const page = await context.newPage();
  await page.goto(`/s/${first.id}`);
  await expect(
    page.getByText("카카오톡 안의 브라우저로 열렸어요"),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "명함 저장" })).toBeEnabled();
  await context.close();
});
