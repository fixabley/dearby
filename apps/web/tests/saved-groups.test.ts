import assert from "node:assert/strict";
import { test } from "node:test";
import { noActivityGroup, savedByActivity } from "../src/lib/saved-groups";
import type { Card } from "../src/lib/models";
import data from "./card-shares.json";

const items = data.cards
  .filter((card) => card.id !== data.revokedCardId)
  .map((card) => ({
    ...card,
    histories: [],
  })) as unknown as Card[];
const shares = data.afterRevocation.guestCards.body.shares;

test("cards repeat under every shared activity and unshared ones come last", () => {
  const groups = savedByActivity(items, shares);
  assert.deepEqual(
    groups.map((group) => [
      group.title,
      group.cards.map((card) => card.profileName),
    ]),
    [
      ["커넥트 IT 컨퍼런스 (테스트)", ["테스트 지민"]],
      ["디자인 해커톤 (테스트)", ["테스트 지민", "테스트 서연"]],
      ["개발자 밋업 (테스트)", ["테스트 지민"]],
    ],
  );
});
test("cards without any share activity form the trailing 활동 없음 group", () => {
  const groups = savedByActivity(items, []);
  assert.deepEqual(
    groups.map((group) => group.id),
    [noActivityGroup],
  );
  assert.equal(groups[0].title, "활동 없음");
  assert.equal(groups[0].cards.length, 2);
  assert.deepEqual(savedByActivity([], shares), []);
});
