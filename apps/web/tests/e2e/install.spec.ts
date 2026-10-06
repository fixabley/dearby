import { test, expect } from "@playwright/test";

test("home-screen manifest, icons and Apple meta without a service worker", async ({
  page,
  request,
}) => {
  const response = await request.get("/manifest.webmanifest");
  expect(response.ok()).toBe(true);
  const manifest = await response.json();
  expect(manifest).toMatchObject({
    name: "Dearby",
    start_url: "/saved",
    scope: "/",
    display: "standalone",
  });
  for (const icon of manifest.icons) {
    const file = await request.get(icon.src);
    expect(file.headers()["content-type"]).toBe("image/png");
  }
  await page.goto("/saved");
  const head = page.locator("head");
  await expect(head.locator('link[rel="manifest"]')).toHaveCount(1);
  await expect(head.locator('link[rel="apple-touch-icon"]')).toHaveCount(1);
  await expect(
    head.locator('meta[name="apple-mobile-web-app-title"]'),
  ).toHaveAttribute("content", "Dearby");
  expect(
    await page.evaluate(() => navigator.serviceWorker.getRegistrations()),
  ).toHaveLength(0);
  // Chrome's own installability check: no errors means installable without a service worker.
  const cdp = await page.context().newCDPSession(page);
  const { installabilityErrors } = await cdp.send(
    "Page.getInstallabilityErrors",
  );
  expect(installabilityErrors).toEqual([]);
});
