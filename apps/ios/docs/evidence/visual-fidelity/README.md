# Approved native visual fidelity — iOS

2026-09-27 KST. Branch `feat/ios-visual-fidelity`, clean start from `c86f809`. Only this iOS checkout and the iOS handoff document changed. No push, production sample records, web app, new design mockup, or common-contract change.

## Reference / before / actual

- [Side-by-side comparison](comparison.html): unchanged approved PNGs beside actual Simulator screenshots; ignore the reference device frames and generated sample text.
- References: [visual contract](../../../../../docs/design/native-visual-contract.md), [approved manifest](../../../../../docs/design/approved/manifest.json).
- Fresh before: [detail](before/detail.png), [profile](before/test-captures/757F6C4A-003F-41AF-8757-56574C0843A5.png), [profile editor](before/test-captures/52834334-8E70-49D8-B63B-A96B70D9C615.png). Captured this task before modifying UI. The initial integration URL52777 had stopped; the before authenticated flow therefore failed after its cached profile capture. These images are visual evidence, not a passing before regression run. No valid before-home capture is claimed.
- Final actual images: [discovery](after/discovery.png), [detail top](after/detail-top.png), [detail bottom](after/detail-bottom.png), [applied strip](after/detail-applied.png), [profile](after/profile.png), [QR](after/qr.png), [QR-only](after/qr-enlarged.png), [share sheet](after/qr-share.png), [new-card state](after/qr-new-card.png), [wallet](after/wallet.png), [send picker](after/send-card-picker.png), [editor](after/card-editor.png), [history selection](after/card-editor-history.png), [shared card](after/shared-card.png), [save confirmation](after/guest-save-confirmation.png), [guest profile](after/profile-guest.png).
- Wallet/send screenshots above show scrolled content. [Wallet top](after/wallet-top.png) and [send top](after/send-card-picker-top.png) show search/groups, prompt and stack composition before scrolling.
- [Largest text profile](after/large-profile.png), [QR](after/large-qr.png), [wallet](after/large-wallet.png), [send](after/large-send.png) are accessibility-category checks, not normal-size visual references.
- [Image provenance](after/manifest.json) records source tests, original attachment names, device and timestamps. Images are unedited XCUITest screenshots. Intermediate duplicate PNGs, xcresult bundles, videos and failed launcher artifacts are not committed.

## Visible corrections and remaining differences

Native SwiftUI content now uses the approved unchanged logo, white background, teal controls, quiet-gray secondary text, flat five-tab bar and fixed detail CTA. Detail routes hide the tab bar; the applied indicator is a thin strip beneath a white navigation bar. Discovery uses square honest image placeholders, compact category chips and left-image/right-information rows. It has one currently verified open activity from the real catalog, not the reference's fictional multi-row data. Thumbnail URLs and the conference/club categories are absent from the shared API; root tracks that gap in #51.

Profile uses the dark-teal initial, icon/name/value contact rows and compact vertical timeline. Shared cards use the mint initial, large contact actions and public timeline. Wallet/send share layered mint cards with permanent name/job headers and no wraparound duplicate back cards. Send headers include the card-name badge. Group counts count owners, and horizontal group switching remains separate from vertical card paging. Editor shows contact slashes/state/toast, history-only select-all, individual checks and a noninteractive preview. The original API/auth/privacy/ID-only guest storage/idempotency boundaries remain.

QR remains a large, real generated code (maximum320pt with a quiet zone), with10pt/8pt scaled text, outside view-card button, horizontal card tiles, native sharing and photo authorization. Long content scrolls; QR is not reduced to force all tiles into one viewport. Camera launch remains an honest launch control because a live camera feed is not available until the native scanner opens. The approved reference's OS-independent chrome differs from iOS27 system back/sheet/toolbar controls. Those native controls were retained rather than painted into fake controls.

Large text uses scrolling and vertical identity/card headers, hides decorative stacked back cards, and exposes explicit previous/next controls. Navigation labels cap at xxxLarge with the large-content viewer. On the authorized iPhone17 Simulator, the maximum accessibility size navigated profile → QR-only → wallet → send successfully. A separate small-screen device and VoiceOver audio traversal were not run; no other worker's simulator was touched.

## Verification

[Compact executed-result evidence](verification.txt).

Device exclusively `B3BE9C9D-9B2E-43BE-8D16-D4F89A97C9BE` (iPhone17, iOS27), Xcode27. Coordinator-owned API `http://127.0.0.1:57937`; real URLSession/SwiftData/Keychain, local test mail sink rather than real email delivery. Profile/contact/history/card records are explicit fixtures inside `unitTests/LocalAPIIntegrationTests.swift` only, published through the real local API. OTP/token values are never printed or committed. The app has no default production API URL.

- 16 Harmonize/SwiftSyntax structure/fixture tests passed.
- Strict SwiftLint0.65.1:57 files, zero violations.
- Debug build-for-testing and Release Simulator build passed with default signing; no `CODE_SIGNING_ALLOWED=NO` override.
- 29 selected domain/storage/contract/real-catalog tests passed at13:33:24 KST, including the opt-in visual fixture setup. A second29-test run at13:43 included bodyless actual logout in place of fixture creation: local session cleared with no remote-revocation fallback warning, and GET/profile with the revoked token returned401.
- Real OTP-fixture login/publish/public projection/import regression passed at13:15:08. Public projection excluded the hidden contact and unselected history.
- Catalog persistence + authenticated profile/QR/wallet/editor + largest-text navigation:3 XCUITests passed at13:43:14,142.981s. Catalog checks source-only no prompt, explicit application self-report/not-now, local program/organization save, restart restoration and report correction. Editor checks history select-all toggling.
- Guest tabs/login gate passed at13:44; shared-card warning/cancel/return-login cancellation passed at13:46:48. The follow-up at13:48:10 additionally passed the assertion that cancelled saving leaves the guest wallet empty (25.706s).
- Final source-presentation + authenticated capture/history-only select-all regression:2 tests passed at13:52:57,64.041s. The explicit assertion confirms history select-all leaves the contact hidden. Latest profile shows unwrapped Behance; wallet/send top captures show their complete upper composition. Test login restoration passed at13:49:03. Additional timestamps are in the manifest and handoff. No physical photo-library/camera permission, external form submission, push/calendar, deployed authentication or Universal Link success is claimed.

Reproduce from repository root (supply the coordinator-owned local API only for Debug integration):

```sh
bash apps/ios/tests/run_architecture.sh
bash apps/ios/tests/run_swiftlint.sh
xcodebuild -project apps/ios/Dearby.xcodeproj -scheme Dearby -destination 'platform=iOS Simulator,id=B3BE9C9D-9B2E-43BE-8D16-D4F89A97C9BE' -derivedDataPath apps/ios/.build build-for-testing DEARBY_API_URL=http://127.0.0.1:57937
xcodebuild -project apps/ios/Dearby.xcodeproj -scheme Dearby -configuration Release -destination 'generic/platform=iOS Simulator' -derivedDataPath apps/ios/.build/release-visual build
```

UI tests run with `-parallel-testing-enabled NO`; authenticated/guest phases are separate because the logout assertion intentionally revokes the session. SharedCardTests requires the explicit public local-test card URL in the test runner's `DEARBY_AUDIT_CARD_URL`, supplied through a local xctestrun copy. Missing test fixtures skip explicitly rather than inject samples into production code.

## Iteration / review record

Early captures exposed a hidden native TabView still reserving its floating bar, detail tab visibility, fixed-action occlusion and low secondary contrast. The final shell uses native NavigationStack with retained catalog paths and a flat bar. An attempted hidden-stack approach was removed. Tests initially treated offscreen/occluded buttons as hittable; viewport guards now use the actual tab boundary and edge scrolling. Catalog tests then passed. A top-state screenshot retry initially over-scrolled a native sheet and dismissed it; the test now captures the send prompt immediately after presentation. Other intermediate failures were the stopped old API, invalid fixture date formatting, a test-only unsupported selector, and iOS27 hiding the cancel-role button in a confirmation popover; explicit visible cancel fixed the latter. Failed bundles are not counted as passing evidence.

Ponytail review removed duplicate recipient ID/snapshot state in favor of one transient public CardModel and kept only actually reused card/timeline/button primitives. No repository/service wrapper, theme engine, navigation engine, or speculative dependency was added. Correctness/accessibility/storage checks are reported separately above. Final image review caught and corrected Behance glyph wrapping without shrinking the button hit target.

## CardDeck clipping follow-up — 2026-09-27 13:59 KST

Source commit `02cd13082aad54f92fff25577d326d9e0620d41b`. Only CardDeck changed: opaque existing mint for the intermediate card, title3-scaled 56pt reveal instead of 34pt, and one-line back headers with a minimum 0.8 scale to accommodate the longer variant badge. The stack is 44pt taller at the default size with two back cards; existing scrolling handles the extra height. Selected-card text, selection/swipe, accessibility-category back-card hiding and delivery/idempotency logic are unchanged.

[Corrected actual picker top](after/send-card-picker-top.png) replaces the prior clipped image. Visually inspected: both back-card names/jobs and variant badges are complete, without glyph clipping or transparency bleed. The first 56pt-only attempt still wrapped the middle name; the final one-line treatment fixes that observed case. Very long arbitrary names can still truncate under the normal one-line policy; this task does not claim an exhaustive content/layout matrix.

Targeted Debug build passed; strict SwiftLint 0.65.1 on CardView.swift passed with zero violations, including after the final adjustment. Final rebuild plus one authenticated capture-only XCUITest passed at13:59:15 KST (10.079s, zero failures): profile edit control proves login, wallet send action opens the sheet, and `navigationBars["내 명함 선택"]` is asserted before the sole screenshot. The existing `testAuthenticatedProfileAndQR` body was temporarily narrowed for capture and restored byte-for-byte afterward; this follow-up did not rerun or claim the full test flow. One prior targeted capture passed at13:58:20 and exposed the wrapped-name issue; only the corrected PNG is retained. Device and API remained the assigned iPhone17 Simulator and localhost57937. Original attachment/time/source hash and PNG SHA256 are in the manifest.

Orca emulator attach failed because its helper could not load SimulatorKit from the installed Xcode paths; XCUITest supplied actual simulator evidence instead, with no configuration changes. Broad suite, Release and accessibility-size flows were not rerun per the narrow task. Ponytail review: Lean already. Ship. Correctness inspection confirms the source diff only affects back-header presentation and reveal spacing. Local logs/result: `/tmp/dearby-carddeck-final.log`, `/tmp/dearby-carddeck-final.xcresult`; no secrets or temporary test changes committed.
