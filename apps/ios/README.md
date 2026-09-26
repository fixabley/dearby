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
