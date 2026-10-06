import { test, expect } from "@playwright/test";
import data from "../card-shares.json";

const headers = {
  "content-type": "application/json",
  "x-dearby-request": "1",
  origin: "http://localhost:3210",
};
const startUrl = async (
  request: import("@playwright/test").APIRequestContext,
) => {
  const response = await request.get("/manifest.webmanifest");
  expect(response.headers()["cache-control"]).toMatch(/private, no-store/);
  expect(response.headers()["vary"]).toMatch(/Cookie/);
  return (await response.json()).start_url as string;
};

test("home-screen handoff: the manifest carries a one-time code that moves the session once", async ({
  browser,
  request,
}) => {
  expect(await startUrl(request)).toBe("/saved");
  await request.put(`/api/guest/shares/${data.shares[0].id}`, {
    headers,
    data: {},
  });
  const url = await startUrl(request);
  expect(url).toMatch(/^\/saved\?handoff=[A-Za-z0-9_-]{43,}$/);
  const redirect = await (
    await browser.newContext()
  ).request.get(url, { maxRedirects: 0 });
  expect(redirect.status()).toBe(303);
  expect(redirect.headers()["location"]).toMatch(/\/saved$/);
  expect(redirect.headers()["referrer-policy"]).toBe("no-referrer");
  expect(redirect.headers()["set-cookie"]).toMatch(/dearby_guest_dev=/);
  // Opening the same code again finds it used and lands on an empty list.
  const reused = await browser.newContext();
  const page = await reused.newPage();
  await page.goto(url);
  expect(new URL(page.url()).search).toBe("");
  await expect(
    page.getByRole("heading", { name: "아직 저장한 명함이 없어요" }),
  ).toBeVisible();
  await reused.close();
});

test("a fresh home-screen app sees the same saved cards; an existing cookie is never replaced", async ({
  browser,
  request,
}) => {
  await request.put(`/api/guest/shares/${data.shares[0].id}`, {
    headers,
    data: {},
  });
  const url = await startUrl(request);
  const app = await browser.newContext();
  const page = await app.newPage();
  await page.goto(url);
  expect(new URL(page.url()).pathname + new URL(page.url()).search).toBe(
    "/saved",
  );
  await expect(page.getByText("테스트 지민")).toBeVisible();
  // A browser that already has its own session keeps it and just drops the query.
  const other = await startUrl(request);
  await page.goto(other);
  expect(new URL(page.url()).search).toBe("");
  await app.close();
});
