import assert from "node:assert/strict";
import { test } from "node:test";
import { inAppBrowser, isIosSafari } from "../src/lib/install";
const iosSafari =
  "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1";
const androidChrome =
  "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36";
test("known in-app browsers are named; regular browsers are not", () => {
  assert.equal(
    inAppBrowser(iosSafari.replace("Safari/604.1", "KAKAOTALK 10.9.0")),
    "카카오톡",
  );
  assert.equal(
    inAppBrowser(`${androidChrome} NAVER(inapp; search; 2000; 12.0.0)`),
    "네이버 앱",
  );
  assert.equal(inAppBrowser(`${iosSafari} Instagram 350.0.0`), "인스타그램");
  assert.equal(
    inAppBrowser(androidChrome.replace("Pixel 9)", "Pixel 9; wv)")),
    "앱 내장 브라우저",
  );
  assert.equal(inAppBrowser(iosSafari), undefined);
  assert.equal(inAppBrowser(androidChrome), undefined);
});
test("install guidance targets only iOS Safari, including iPadOS desktop UA", () => {
  assert.equal(isIosSafari(iosSafari), true);
  assert.equal(
    isIosSafari(iosSafari.replace("Version/18.5", "CriOS/140.0")),
    false,
  );
  assert.equal(isIosSafari(`${iosSafari} KAKAOTALK 10.9.0`), false);
  assert.equal(isIosSafari(androidChrome), false);
  const iPad =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Safari/605.1.15";
  assert.equal(isIosSafari(iPad, 5), true);
  assert.equal(isIosSafari(iPad, 0), false);
});
