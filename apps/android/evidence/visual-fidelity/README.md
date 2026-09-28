# Android approved visual implementation evidence — 2026-09-27

Scope: Issue #50 / PR40, branch `feat/android-visual-fidelity`, base `c86f809`. Only approved images in `docs/design/approved` and `docs/design/native-visual-contract.md` were used as visual references. All screenshots were freshly rendered on emulator-5554 (API36) in this execution.

## Review entry points

- [Side-by-side gallery](compare.html): approved image (outer phone frame cropped for comparison), baseline, updated native screen.
- `before/fixtures/*.png`: baseline APK built from the task base, rendered with the existing instrumentation UI fixtures. Gradle uninstalled the initial test app, so the same baseline APK was reinstalled and tested using `adb shell am instrument` to preserve captures.
- `after/visual-*.png`: richer test-only fixtures, three received people; the send picker uses three card variants belonging to the same person. Contacts and three history entries are artificial test data, absent from the release APK.
- `after/app-*.png`: complete real MainActivity and Android system bars using the coordinator's isolated API and authorized `android@example.test` account. This is a real API integration, not production user data. OTP/token files and raw response bodies are excluded.
- `after/fixture-catalog-*.png`: controlled current/unknown/self-reported catalog states.

## Behavior and visual changes

White/teal palette, original approved logo PNG, 10–12dp corners, scalable typography, 48dp interactive targets. Discovery uses image-left/info-right rows and a bookmark action; detail keeps source/self-report controls and a fixed bottom official-site/save action. Profile uses a filled teal initial, contact icon/name/value rows, and a vertical history line. QR dominates its card; share opens a native bottom sheet, mode controls announce selection, new-card selection opens an invitation, and activity context remains available through a collapsed entry. Wallet/send use actual neighboring card headers, separate detail dialogs, and explicit outer actions. Send headers retain person/job plus the card-variant title; busy submission blocks changing selection/context/creation. Shared cards require successful public lookup and explicit local-only storage confirmation; login returns to the send picker without sending. Editor contact choices start private; history select-all never selects contacts.

## Intended differences and limits

- No verified image URL or conference/club taxonomy exists in the API. Thumbnails honestly say image unavailable, and type chips use actual registration/selection values. Coordinator tracks common taxonomy work; the Android client does not invent facts.
- Source image sample names/events/dates are not shipped as production content. Empty and sparse real-account screens have correspondingly less content than the rich reference.
- QR scan uses the actual external camera/photo decoder. It does not draw a fake live camera preview or a nonfunctional flashlight.
- Calendar conflicts, push, autofill, public HTTPS QR/App Links and production email deployment remain outside this implementation. No fake completion or calendar button was added.
- The editor retains the required card name/description fields; all contacts remain private initially. Existing publication and API contracts are unchanged.
- Dense card contents scroll inside the card at large font sizes; top identity and outer action remain outside the scroll. A 320×568dp, 1.6 fontScale editor check is separate from normal-size reference fidelity.
- Native Android dialogs (explicit white surface), system share, camera, and back behavior are preserved. Generated phone frames, paper grain and malformed reference lettering are not rendered.

## Verification (current execution)

JDK Android Studio JBR; Android SDK36; emulator-5554 only.

```sh
python3 apps/android/scripts/check-fsd.py --self-test
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' apps/android/gradlew -p apps/android :app:assembleDebug :app:assembleDebugAndroidTest -PdearbyApiUrl=http://10.0.2.2:57937
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' apps/android/gradlew -p apps/android :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease
adb -s emulator-5554 shell am instrument -w -e class com.dearby.nativeapp.ScreenTest,com.dearby.nativeapp.CatalogScreenTest com.dearby.nativeapp.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class com.dearby.nativeapp.VisualFidelityTest,com.dearby.nativeapp.FullAppVisualTest#authenticatedTabsAndCardPreview -e localApi true com.dearby.nativeapp.test/androidx.test.runner.AndroidJUnitRunner
```

The local API build property is only a test invocation, never a production default. Normal Release build uses the empty configured origin. Full app tests require the existing opt-in LocalApiIntegrationTest login setup and private-file OTP handoff; secrets must never be printed.

- Baseline existing UI: 14/14 passed.
- Final combined UI: 26/26 passed (existing screens 14, rich visual/privacy/accessibility tests 11, real MainActivity 1). Final warning copy/white dialog/share menu targeted rerun: 2/2 passed.
- JVM: 28 passed, zero failures.
- FSD: 45 Kotlin files; 28 checker self-tests passed.
- Debug and Release assemble: passed. Debug/Release lint: zero errors, 17 version/KTX recommendations each.
- Persistence/QR/actual catalog suite: 6 passed, one opt-in restart check skipped in that invocation; the restart check was then explicitly run separately and passed (1/1). Covers Room reopen, failed commit preservation, guest partial import, QR pixels/Keystore, actual public catalog, local bookmarks/self-report, real application browser and context selection.
- First full app test missed a new-card tap under a transient snackbar. It now waits for snackbar dismissal and passed on rerun.
- Real guest/login-return flow: 1/1 passed, including logout, explicit save warning cancellation, login cancellation, real OTP login, picker return and cancellation back to the shared card without sending.
- Native Android HTTP framing: 1/1 passed. This exposed Issue #52: Android added form Content-Type to an empty DELETE and the server rejected logout with 422. The client now explicitly sends text/plain for bodyless DELETE, preserving zero bytes and the server contract.
- Actual API authentication/profile/publication/import integration passed. Tests never claim external application submission; catalog application state is explicitly self-reported.

## Complexity review

Ponytail review removed five unused imports after moving screen composition. Reused visual primitives and actual card/timeline/button components have concrete callers; no theme engine, navigation engine, new icon library or domain wrapper was added. Correctness/accessibility/storage checks above are separate from this complexity review.

## Final review and artifacts

Coordinator reviewed approved originals against the final QR/picker and earlier profile/wallet/shared captures and accepted the correction. QR tiles may require scrolling because the approved QR emphasis is preserved. Final save warning reads: “이 기기에 명함을 저장해요. 로그인하지 않고 저장한 명함은 앱을 삭제하면 복구할 수 없어요.” Screenshots are indexed with dimensions, provenance and SHA-256 in `manifest.json`; no APK, raw integration response, credential, OTP or emulator-private file is included. Physical-device/TalkBack and production-service validation were not performed.
