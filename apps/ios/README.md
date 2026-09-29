# Dearby iOS

Open `Dearby.xcodeproj`, scheme `Dearby`. Xcode 27 / Swift 6, iOS 18+.

```sh
xcodebuild -project apps/ios/Dearby.xcodeproj -scheme Dearby -destination 'generic/platform=iOS Simulator' -derivedDataPath apps/ios/.build build
xcodebuild -project apps/ios/Dearby.xcodeproj -scheme Dearby -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath apps/ios/.build test
bash apps/ios/scripts/setup_swiftlint.sh
bash apps/ios/tests/run_swiftlint.sh
bash apps/ios/tests/run_architecture.sh
```

Use normal Simulator ad-hoc signing for Keychain tests; disabling signing causes Keychain writes to fail. The committed project needs no generator to build. After adding source files, `gem install --user-install xcodeproj -v 1.28.1` then `ruby apps/ios/scripts/generate_project.rb` regenerates it (review changes; this replaces project settings).

Set `DEARBY_API_URL` build setting to the real API **origin**, e.g. Debug loopback `http://127.0.0.1:<port>`; client appends `/v1` exactly once. Defaults to empty. Release requires HTTPS. `Info-Debug.plist` alone permits local networking. Optional `DEARBY_SHARE_URL` is the approved HTTPS card URL prefix; otherwise generated QR uses the agreed `dearby://card/<UUID>?label=...` installed-app development link. No bundled credentials or fake logged-in identity.

Email OTP uses actual HTTP and Keychain. Logged-out profile and creation show login. Guest QR/photo/ID reception queries the public card then asks before durable ID saving. Camera uses VisionKit; photos use PhotosPicker/Vision; image saving requests add-only Photos permission. Physical camera/photo/permission combinations are unverified (#43). Discovery/saved activity tabs explicitly show unavailable states; this is not the complete service (#41).

## Opt-in local API integration

A coordinator must start a **test-only** loopback API with isolated database and private mail sink. Prepare the Simulator fixture with `scripts/prepare_local_integration.py --origin <loopback-origin> --inbox <private-mail-json> --simulator <UDID>`, then run `-only-testing:DearbyTests/LocalAPIIntegrationTests` with matching `DEARBY_API_URL`. The script requests only `ios@example.test`, never prints codes/tokens, and the native test deletes the private credential file before use. Only public test card/profile IDs are exported to Documents/dearby-integration-result.json. This does not prove real email delivery or production auth. Without the explicit private fixture this integration test skips.

`DearbyUITests/NavigationTests/testGuestTabsAndLoginGate` verifies the default unconfigured guest build. `testAuthenticatedProfileAndQR` requires the real local integration session; it skips without one. Evidence and precise limits are in docs/evidence/README.md.

## Distribution metadata

`MARKETING_VERSION=0.1.0` and `CURRENT_PROJECT_VERSION=1` are **provisional**: the coordinator must check existing App Store Connect records before upload. The project and generator both select `AppIcon`; provenance and the existing Android launcher choice are documented in `Resources/ASSET-SOURCES.md`. iPad declares all four orientations, iPhone portrait and both landscapes. The missing AccentColor asset reference is cleared; the app's explicit teal tint remains.

`ITSAppUsesNonExemptEncryption=NO` is based on this app's OS-provided URLSession HTTPS and Keychain use, with no bundled crypto implementation or third-party runtime dependency found. This is a technical configuration, not a legal determination; the distributor must confirm the final declaration when uploading and reassess when dependencies change. Team/signing and the real HTTPS API origin remain coordinator-owned. See `../../docs/context/testflight-main-readiness.md` for actual archive checks and remaining blockers.
