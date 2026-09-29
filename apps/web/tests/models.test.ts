import assert from "node:assert/strict";
import { test } from "node:test";
import { contactHref, isRecruiting, safeWebUrl } from "../src/lib/models";
import { card, catalog } from "./fixtures";
test("expired, unverified, future or closed activities never remain in discovery", () => {
  const now = Date.now(),
    activity = catalog(now).activities[0];
  assert.equal(isRecruiting(activity, now), true);
  assert.equal(isRecruiting(activity, now + 3600000), false);
  assert.equal(isRecruiting({ ...activity, freshness: "stale" }, now), false);
  assert.equal(
    isRecruiting({ ...activity, recruitmentStatus: "closed" }, now),
    false,
  );
  assert.equal(
    isRecruiting(
      { ...activity, recruitmentStartAt: new Date(now + 1000).toISOString() },
      now,
    ),
    false,
  );
  assert.equal(
    isRecruiting({ ...activity, validUntil: "invalid" }, now),
    false,
  );
});
test("untrusted external/contact values cannot become script or credential-bearing links", () => {
  for (const value of [
    "javascript:alert(1)",
    "data:text/html,test",
    "https://user:pass@example.test",
    "not a URL",
  ])
    assert.equal(safeWebUrl(value), undefined);
  assert.equal(
    contactHref({
      ...card.contacts[0],
      value: "a@example.test?bcc=private@example.test",
    }),
    undefined,
  );
  assert.equal(contactHref(card.contacts[0]), "mailto:public@example.test");
  assert.equal(
    safeWebUrl("https://example.test/path"),
    "https://example.test/path",
  );
});
