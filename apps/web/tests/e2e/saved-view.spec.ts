import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import data from "../card-shares.json";

const [twoActivities, , , noActivity] = data.shares;

async function save(page: Page, shareId: string) {
  await page.goto(`/s/${shareId}`);
  await page.getByRole("button", { name: "명함 저장" }).click();
  await expect(page.getByText("이 브라우저에만 저장됐어요.")).toBeVisible();
}

test("saved cards switch between all and by-activity groups; a card repeats per activity and none comes last", async ({
  page,
}) => {
  await save(page, twoActivities.id);
  await save(page, noActivity.id);
  await page.goto("/saved");
  await expect(page.getByRole("button", { name: "전체" })).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  await page.getByRole("button", { name: "활동별" }).click();
  const headers = page.locator("section > h2");
  await expect(headers).toHaveText([
    /커넥트 IT 컨퍼런스 \(테스트\)\s*1/,
    /디자인 해커톤 \(테스트\)\s*1/,
    /활동 없음\s*1/,
  ]);
  await expect(page.getByRole("heading", { name: "테스트 지민" })).toHaveCount(
    2,
  );
  await expect(page.getByRole("heading", { name: "테스트 서연" })).toHaveCount(
    1,
  );
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  // Folding one group hides only its cards.
  await page.getByRole("button", { name: /활동 없음/ }).click();
  await expect(page.getByRole("button", { name: /활동 없음/ })).toHaveAttribute(
    "aria-expanded",
    "false",
  );
  await expect(page.getByRole("heading", { name: "테스트 서연" })).toHaveCount(
    0,
  );
  await page.getByRole("button", { name: "전체" }).click();
  await expect(page.getByRole("heading", { name: "테스트 지민" })).toHaveCount(
    1,
  );
  await expect(page.getByRole("heading", { name: "테스트 서연" })).toHaveCount(
    1,
  );
});

test("after saving, iOS Safari gets the Add to Home Screen steps and 나중에 keeps it hidden", async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "iPhone user agent only");
  await save(page, twoActivities.id);
  const offer = page.getByRole("region", { name: "홈 화면에 추가" });
  await expect(offer).toBeVisible();
  await expect(offer).toContainText("홈 화면에 추가");
  await expect(
    offer.getByRole("button", { name: "홈 화면에 추가" }),
  ).toHaveCount(0);
  await page.goto("/saved");
  await expect(offer).toBeVisible();
  await offer.getByRole("button", { name: "나중에" }).click();
  await expect(offer).toBeHidden();
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "테스트 지민" }),
  ).toBeVisible();
  await expect(offer).toBeHidden();
});

test("Chrome's install event shows an install button that calls the browser prompt", async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "desktop Chrome only");
  await save(page, twoActivities.id);
  const offer = page.getByRole("region", { name: "홈 화면에 추가" });
  // Desktop Chrome is not iOS: nothing until the browser offers an install.
  await expect(offer).toBeHidden();
  await page.evaluate(() => {
    const event = Object.assign(
      new Event("beforeinstallprompt", { cancelable: true }),
      {
        prompt: () => {
          (window as unknown as { prompted: boolean }).prompted = true;
          return Promise.resolve();
        },
      },
    );
    dispatchEvent(event);
  });
  await offer.getByRole("button", { name: "홈 화면에 추가" }).click();
  expect(
    await page.evaluate(
      () => (window as unknown as { prompted?: boolean }).prompted,
    ),
  ).toBe(true);
});
