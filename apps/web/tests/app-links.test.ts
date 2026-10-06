import assert from "node:assert/strict";
import { test } from "node:test";
import { appleAppSiteAssociation, assetLinks } from "../src/lib/app-links";
const fingerprint = Array(32).fill("ab").join(":");
test("apple association lists every bundle under the team for /s/* only", () => {
  assert.deepEqual(
    appleAppSiteAssociation({
      DEARBY_APPLE_TEAM_ID: "ABCDE12345",
      DEARBY_IOS_BUNDLE_IDS: " com.example.app , com.example.debug ",
    }),
    {
      applinks: {
        details: [
          {
            appIDs: [
              "ABCDE12345.com.example.app",
              "ABCDE12345.com.example.debug",
            ],
            components: [{ "/": "/s/*" }],
          },
        ],
      },
    },
  );
});
test("missing or malformed values publish nothing", () => {
  assert.equal(appleAppSiteAssociation({}), undefined);
  assert.equal(
    appleAppSiteAssociation({ DEARBY_APPLE_TEAM_ID: "ABCDE12345" }),
    undefined,
  );
  assert.equal(
    appleAppSiteAssociation({
      DEARBY_APPLE_TEAM_ID: "short",
      DEARBY_IOS_BUNDLE_IDS: "com.example.app",
    }),
    undefined,
  );
  assert.equal(assetLinks({}), undefined);
  assert.equal(
    assetLinks({
      DEARBY_ANDROID_PACKAGE: "com.example.app",
      DEARBY_ANDROID_CERT_SHA256: "not-a-fingerprint",
    }),
    undefined,
  );
});
test("asset links normalize fingerprints to upper case", () => {
  const [statement] = assetLinks({
    DEARBY_ANDROID_PACKAGE: "com.example.app",
    DEARBY_ANDROID_CERT_SHA256: fingerprint,
  })!;
  assert.deepEqual(statement.target, {
    namespace: "android_app",
    package_name: "com.example.app",
    sha256_cert_fingerprints: [fingerprint.toUpperCase()],
  });
});
