/** Explicit opt-in test against an isolated, pre-seeded local API. Revokes card 3. */
import assert from "node:assert/strict";
import { chromium, expect } from "@playwright/test";

async function main() {
  const web = process.env.WEB_TEST_ORIGIN;
  const api = process.env.API_TEST_ORIGIN;
  const owner = process.env.API_TEST_OWNER_TOKEN;
  for (const origin of [web, api]) {
    assert.ok(
      origin && /^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(origin),
      "Explicit isolated localhost origins required",
    );
  }
  assert.ok(owner, "Explicit isolated test owner token required");
  const id2 = "00000000-0000-4000-8000-000000000002";
  const id3 = "00000000-0000-4000-8000-000000000003";
  const browser = await chromium.launch();
  try {
    const context = await browser.newContext();
    const otherContext = await browser.newContext();
    const page = await context.newPage();
    const secondTab = await context.newPage();
    const other = await otherContext.newPage();
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.goto(`${web}/cards/${id2}`);
    await secondTab.goto(`${web}/cards/${id3}`);
    assert.equal((await context.cookies()).length, 0);
    const firstResponse = page.waitForResponse(
      (response) =>
        response.request().method() === "PUT" && response.url().endsWith(id2),
    );
    await Promise.all([
      page.getByRole("button", { name: "명함 저장", exact: true }).click(),
      secondTab.getByRole("button", { name: "명함 저장", exact: true }).click(),
    ]);
    const response = await firstResponse;
    assert.deepEqual(Object.keys(await response.json()).sort(), [
      "cardId",
      "status",
    ]);
    await expect(page.getByRole("status")).toHaveText("명함을 저장했어요.");
    await expect(secondTab.getByRole("status")).toHaveText(
      "명함을 저장했어요.",
    );
    const cookie = (await context.cookies()).find(
      (item) => item.name === "dearby_guest_dev",
    )!;
    assert.ok(cookie.httpOnly && cookie.sameSite === "Lax");
    assert.equal(cookie.value.length, 43);
    assert.ok(cookie.expires > Date.now() / 1000 + 399 * 86400);
    await page.goto(`${web}/saved`);
    await expect(page.locator(".saved-card")).toHaveCount(2);
    assert.equal(
      (await context.cookies()).find((item) => item.name === cookie.name)
        ?.value,
      cookie.value,
    );
    await other.goto(`${web}/saved`);
    await expect(
      other.getByRole("heading", { name: "아직 저장한 명함이 없어요" }),
    ).toBeVisible();
    assert.equal((await otherContext.cookies()).length, 0);
    await other.goto(`${web}/cards/${id2}`);
    await other.getByRole("button", { name: "명함 저장", exact: true }).click();
    await expect(other.getByRole("status")).toHaveText("명함을 저장했어요.");
    assert.notEqual(
      (await otherContext.cookies()).find((item) => item.name === cookie.name)
        ?.value,
      cookie.value,
    );
    await page
      .locator(".saved-card")
      .filter({ has: page.locator(`a[href='/cards/${id2}']`) })
      .getByRole("button")
      .click();
    await expect(page.locator(".saved-card")).toHaveCount(1);
    await other.goto(`${web}/saved`);
    await expect(other.locator(".saved-card")).toHaveCount(1);
    // API test owner is authorized only for the disposable fixture, never a production credential.
    const revoked = await fetch(`${api}/v1/cards/${id3}`, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${owner}` },
    });
    assert.equal(revoked.status, 204);
    await page.goto(`${web}/cards/${id3}`);
    await expect(
      page.getByRole("heading", { name: "명함을 볼 수 없어요" }),
    ).toBeVisible();
    assert.equal(
      (await context.request.get(`${web}/api/cards/${id3}`)).status(),
      404,
    );
    assert.equal(
      (
        await context.request.put(`${web}/api/guest/cards/${id3}`, {
          headers: {
            Origin: web!,
            "X-Dearby-Request": "1",
            "Content-Type": "application/json",
          },
          data: {},
        })
      ).status(),
      404,
    );
    await page.goto(`${web}/saved`);
    await expect(
      page.getByRole("heading", { name: "아직 저장한 명함이 없어요" }),
    ).toBeVisible();
    other.once("dialog", (dialog) => dialog.accept());
    await other
      .getByRole("button", { name: "이 브라우저의 저장 세션 삭제" })
      .click();
    await expect(
      other.getByRole("heading", { name: "아직 저장한 명함이 없어요" }),
    ).toBeVisible();
    assert.equal((await otherContext.cookies()).length, 0);
    assert.deepEqual(errors, []);
    console.log(
      "PASS real API: atomic two-tab save, no token JSON, 400-day same-token cookie, two-browser isolation, delete isolation, revoke 404/list removal, explicit session deletion, page errors 0",
    );
    console.log(
      "Local HTTP dev cookie only; production Secure cookie is separately covered by proxy unit tests and must be verified on deployed HTTPS.",
    );
  } finally {
    await browser.close();
  }
}
main().catch(() => {
  console.error(
    "Real API integration failed; inspect the assertion locally without logging credentials.",
  );
  process.exitCode = 1;
});
