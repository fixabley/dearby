import { test, expect } from "@playwright/test";
import data from "../card-shares.json";

const headers = {
  "content-type": "application/json",
  "x-dearby-request": "1",
  origin: "http://localhost:3210",
};
const [first, second, , , revoked] = data.shares;

test("share proxy: public share, one-call save with guest cookie, records in saved list", async ({
  request,
}) => {
  const page = await request.get(`/api/shares/${first.id}`);
  expect(page.status()).toBe(200);
  expect((await page.json()).share.activities).toEqual(first.activities);
  expect((await request.get(`/api/shares/${revoked.id}`)).status()).toBe(404);
  const save = await request.put(`/api/guest/shares/${first.id}`, {
    headers,
    data: {},
  });
  expect(save.status()).toBe(201);
  expect(await save.json()).toEqual({
    cardId: first.cardId,
    shareId: first.id,
    status: "saved",
  });
  expect(
    (
      await request.put(`/api/guest/shares/${second.id}`, { headers, data: {} })
    ).status(),
  ).toBe(200);
  const again = await request.put(`/api/guest/shares/${first.id}`, {
    headers,
    data: {},
  });
  expect((await again.json()).status).toBe("alreadySaved");
  const saved = await (await request.get("/api/guest/cards")).json();
  expect(saved.items.map((card: { id: string }) => card.id)).toEqual([
    first.cardId,
  ]);
  expect(
    saved.shares.map((share: { shareId: string }) => share.shareId),
  ).toEqual([first.id, second.id]);
});
