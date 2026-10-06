import { test, expect } from "@playwright/test";

test("app link association files are JSON without redirects, 404 until configured", async ({
  request,
}) => {
  const apple = await request.get("/.well-known/apple-app-site-association", {
    maxRedirects: 0,
  });
  expect(apple.status()).toBe(200);
  expect(apple.headers()["content-type"]).toMatch(/^application\/json/);
  expect(await apple.json()).toEqual({
    applinks: {
      details: [
        {
          appIDs: ["ABCDE12345.com.example.app"],
          components: [{ "/": "/s/*" }],
        },
      ],
    },
  });
  const android = await request.get("/.well-known/assetlinks.json", {
    maxRedirects: 0,
  });
  expect(android.status()).toBe(404);
});
