import { test } from "node:test";
import assert from "node:assert/strict";
import { parseSaved } from "../src/features/saved/storage";
test("saved data validates shape and isolates organization IDs", () => {
  assert.deepEqual(parseSaved(null), { programs: [], organizations: [] });
  assert.throws(() => parseSaved("{"));
  assert.throws(() => parseSaved('{"programs":[1],"organizations":[]}'));
  assert.deepEqual(
    parseSaved(
      JSON.stringify({
        programs: ["feconf", "feconf", "org-fedg"],
        organizations: ["org-fedg", "feconf"],
      }),
    ),
    { programs: ["feconf"], organizations: ["org-fedg"] },
  );
});
